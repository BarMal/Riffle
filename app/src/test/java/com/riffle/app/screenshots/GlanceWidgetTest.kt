package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.glance.GlanceCallbacks
import com.riffle.app.launcher.glance.GlanceWidgetBody
import com.riffle.core.domain.launcher.glance.GlanceState
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glance widgets (ADR 0003, P11), drawn by the real `GlanceWidgetBody` from typed content: each widget at its
 * minimum and default span, with content, empty, permission required and redacted, on a compact phone and unfolded;
 * plus behaviour checks (transport and primary actions through the one gateway, the body opening the Stream, the menu
 * equivalent at a small span, one accessibility action list, 48 dp targets). The clock, images and locale are fixed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GlanceWidgetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val performed = mutableListOf<Pair<StreamContent, StreamAction>>()
    private var openedStream = 0
    private val accessRequests = mutableListOf<List<SourceId>>()

    private val callbacks =
        GlanceCallbacks(
            perform = { content, action -> performed += content to action },
            openStream = { openedStream++ },
            requestAccess = { accessRequests += it },
        )

    @Test
    fun nowPlayingStates() = states(GlanceWidgetKind.NOW_PLAYING, CELL_COMPACT)

    @Test
    fun nextEventStates() = states(GlanceWidgetKind.NEXT_EVENT, CELL_COMPACT)

    @Test
    fun topStoryStates() = states(GlanceWidgetKind.TOP_STORY, CELL_COMPACT)

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun nowPlayingStatesUnfolded() = states(GlanceWidgetKind.NOW_PLAYING, CELL_EXPANDED)

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun nextEventStatesUnfolded() = states(GlanceWidgetKind.NEXT_EVENT, CELL_EXPANDED)

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun topStoryStatesUnfolded() = states(GlanceWidgetKind.TOP_STORY, CELL_EXPANDED)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowPlayingStatesDark() = states(GlanceWidgetKind.NOW_PLAYING, CELL_COMPACT)

    @Test
    fun withReducedTransparency() {
        render(reducedTransparency = true) {
            Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(4, 2))
            Widget(GlanceWidgetKind.TOP_STORY, GlanceState.Showing(StreamFixtures.articles.first()), GridSpan(4, 3))
        }
        composeRule.captureScreen()
    }

    @Test
    fun theTransportButtonsActThroughTheGatewayAndAre48dpTargets() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(4, 2)) }
        composeRule.onNodeWithContentDescription("Next track").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithContentDescription("Previous track").performClick()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertEquals(
            listOf(StreamAction.Next, StreamAction.Previous, StreamAction.Pause),
            performed.map { it.second },
        )
        assertTrue(performed.all { it.first == StreamFixtures.media })
        assertEquals(0, openedStream)
    }

    @Test
    fun tappingTheNowPlayingTextOpensTheStreamNotThePlayer() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(4, 2)) }
        composeRule.onNodeWithText("Midnight City").performClick()
        assertEquals(1, openedStream)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun theFullNowPlayingCardKeepsTheStreamsTransportAndOpensTheStreamFromItsTitle() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(4, 3)) }
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithText("Midnight City").performClick()
        assertEquals(listOf<StreamAction>(StreamAction.Next), performed.map { it.second })
        assertEquals(1, openedStream)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Show queue").fetchSemanticsNodes().size)
    }

    @Test
    fun aSmallSpanHasTheControlsInAMenu() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(2, 1)) }
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Next track").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("More actions").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Next track").performClick()
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Open Stream").performClick()
        assertEquals(listOf<StreamAction>(StreamAction.Next), performed.map { it.second })
        assertEquals(1, openedStream)
    }

    @Test
    fun theNextEventAndTopStoryPrimaryActionsGoThroughTheGateway() {
        render {
            Widget(GlanceWidgetKind.NEXT_EVENT, GlanceState.Showing(StreamFixtures.nextEvent), GridSpan(4, 2))
            Widget(GlanceWidgetKind.TOP_STORY, GlanceState.Showing(StreamFixtures.articles.first()), GridSpan(4, 2))
        }
        composeRule.onNodeWithText("Design standup").performClick()
        composeRule.onNodeWithText(StreamFixtures.articles.first().title!!).performClick()
        assertEquals(listOf<StreamAction>(StreamAction.Open, StreamAction.Open), performed.map { it.second })
        assertEquals(0, openedStream)
        composeRule.onAllNodesWithContentDescription("More actions")[0].performClick()
        composeRule.onNodeWithText("Open Stream").performClick()
        assertEquals(1, openedStream)
    }

    @Test
    fun theEmptyStateTapsThroughToTheStream() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Empty, GridSpan(4, 2)) }
        composeRule.onNodeWithText("Nothing playing").performClick()
        assertEquals(1, openedStream)
    }

    @Test
    fun thePermissionStateOffersTheStreamsHonestAllowAccessAction() {
        render {
            Widget(
                GlanceWidgetKind.NEXT_EVENT,
                GlanceState.PermissionRequired(listOf(SourceIds.CALENDAR)),
                GridSpan(2, 1),
            )
        }
        composeRule.onNodeWithText("Allow access to show this").assertExists()
        composeRule.onNodeWithText("Allow access").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(listOf(SourceIds.CALENDAR)), accessRequests)
    }

    @Test
    fun aRedactedItemSaysHiddenContentAndKeepsItsTransport() {
        val hidden =
            StreamFixtures.media.copy(title = null, artist = null, artwork = null, privacy = StreamPrivacy.REDACTED)
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(hidden), GridSpan(4, 2)) }
        composeRule.onNodeWithText("Hidden content").assertExists()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        assertEquals(listOf<StreamAction>(StreamAction.Next), performed.map { it.second })
    }

    @Test
    fun eachWidgetIsOneLabelledGroupWithOneListOfActions() {
        render { Widget(GlanceWidgetKind.NOW_PLAYING, GlanceState.Showing(StreamFixtures.media), GridSpan(4, 2)) }
        val group = composeRule.onNode(hasContentDescription("Now playing: Midnight City by M83")).fetchSemanticsNode()
        val labels = group.config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Previous track", "Pause", "Next track", "Open Stream"), labels)
        assertEquals(0, composeRule.onAllNodesWithText("Open Stream").fetchSemanticsNodes().size)
    }

    private fun states(
        kind: GlanceWidgetKind,
        cell: Dp,
    ) {
        render {
            val content = StreamFixtures.contentFor(kind)
            val redacted = StreamFixtures.redactedFor(kind)
            listOf(
                GlanceState.Showing(content) to kind.minSpan,
                GlanceState.Showing(content) to kind.defaultSpan,
                GlanceState.Showing(content) to kind.maxSpan,
                GlanceState.Empty to kind.defaultSpan,
                GlanceState.PermissionRequired(listOf(SourceIds.MEDIA)) to kind.defaultSpan,
                GlanceState.PermissionRequired(listOf(SourceIds.MEDIA)) to kind.minSpan,
                GlanceState.Showing(redacted) to kind.minSpan,
                GlanceState.Showing(redacted) to kind.defaultSpan,
            ).forEach { (state, span) -> Widget(kind, state, span, cell) }
        }
        composeRule.captureScreen()
    }

    private fun render(
        reducedTransparency: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                }
            }
        }
    }

    @Composable
    private fun Widget(
        kind: GlanceWidgetKind,
        state: GlanceState,
        span: GridSpan,
        cell: Dp = CELL_COMPACT,
    ) {
        GlanceWidgetBody(
            kind = kind,
            state = state,
            span = span,
            env = StreamFixtures.environment(),
            callbacks = callbacks,
            modifier = Modifier.size(cell * span.columns, cell * span.rows),
        )
    }

    private companion object {
        val CELL_COMPACT = 82.dp
        val CELL_EXPANDED = 104.dp
    }
}

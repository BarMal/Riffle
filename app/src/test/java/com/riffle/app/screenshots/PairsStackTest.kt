package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.PairEdit
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairDraft
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.PairsSection
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Pairs stack through the real Stream (ADR 0003, Amendment 7): the card's tap, its long-press menu, the draft's
 * halves, the empty page, the mirrored halves in a right-to-left layout, and that a pair never opens an open card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PairsStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()
    private val edits = mutableListOf<PairEdit>()

    private val byIdentity: Map<AppIdentity, String> =
        ScreenshotFixtures.installedApps.associate {
            it.identity to it.label
        }

    private fun resolve(identity: AppIdentity): PairSide? =
        byIdentity[identity]?.let { PairSide(identity, it, ItemImageKeys.appIcon(identity)) }

    private fun pair(
        id: String,
        first: AppIdentity,
        second: AppIdentity,
        opened: Long = 0,
    ) = AppPair(id, first, second, opened)

    private val notes = ScreenshotFixtures.notes.identity
    private val maps = ScreenshotFixtures.maps.identity
    private val mail = ScreenshotFixtures.mail.identity
    private val camera = ScreenshotFixtures.camera.identity

    private fun result(
        pairs: List<AppPair>,
        draft: PairDraft? = null,
    ) = PairsSection.result(pairs, draft, ::resolve)

    private fun show(
        result: StreamSectionResult,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val sources = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) }
        val results = sources + (StreamSection.PAIRS to result)
        composeRule.setContent {
            ScreenshotBackdrop { Surface(results, direction) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Surface(
        results: Map<StreamSection, StreamSectionResult>,
        direction: LayoutDirection,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(modifier = Modifier.fillMaxSize()) {
                StreamSurface(
                    layout = StreamLayout.DEFAULT,
                    results = results,
                    env = StreamFixtures.environment(),
                    callbacks =
                        StreamCallbacks(
                            onAction = { content, action -> actions += content to action },
                            onPairEdit = { edits += it },
                        ),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    initialPageKey = StreamPagePlanner.keyOf(StreamSection.PAIRS),
                )
            }
        }
    }

    @Test
    fun aTapOnTheFrontCardOpensItsTwoAppsAndNeverAnOpenCard() {
        show(result(listOf(pair("a", notes, maps), pair("b", mail, camera))))

        composeRule.onNodeWithText("Tap to open side by side").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").performClick()

        val (content, action) = actions.single()
        assertEquals(StreamAction.Open, action)
        assertEquals(
            listOf("Notes", "Maps"),
            (content as AppPairContent).let { listOf(it.first?.label, it.second?.label) },
        )
        composeRule.onAllNodesWithText("Open side by side").assertCountEquals(0)
    }

    @Test
    fun theFrontCardIsOneButtonSayingWhatItIsWithTheEditsAsActions() {
        show(result(listOf(pair("a", notes, maps))))

        val node =
            composeRule.onNode(hasContentDescription("Notes and Maps, pair. Opens side by side")).fetchSemanticsNode()
        val labels = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Swap sides", "Change first app", "Change second app", "Remove"), labels)
    }

    @Test
    fun aLongPressOffersTheFiveItemsAndNoPinToTheDock() {
        show(result(listOf(pair("a", notes, maps))))

        // The half under the finger is the target of the long-press (the merged card node keeps only the click).
        composeRule.onAllNodesWithText("Notes", useUnmergedTree = true)[0].onParent()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("Open side by side").assertExists()
        composeRule.onNodeWithText("Change first app").assertExists()
        composeRule.onNodeWithText("Change second app").assertExists()
        composeRule.onNodeWithText("Remove").assertExists()
        composeRule.onAllNodesWithText("Pin to the Dock").assertCountEquals(0)
        composeRule.onNodeWithText("Swap sides").performClick()

        assertEquals(listOf<PairEdit>(PairEdit.SwapSides("a")), edits)
    }

    @Test
    fun theDraftHasTwoChooseButtonsThatAskForThePicker() {
        show(result(emptyList(), PairDraft(first = notes)))

        composeRule.onNodeWithText("Notes").assertIsDisplayed()
        composeRule.onNodeWithText("Choose an app").assertIsDisplayed().performClick()

        assertEquals(listOf<PairEdit>(PairEdit.ChooseDraftHalf(secondHalf = true)), edits)
    }

    @Test
    fun theEmptyPageIsTheNewPairCardAloneWithItsLine() {
        show(result(emptyList()))

        composeRule.onNodeWithText("New pair").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Open two apps side by side.").assertIsDisplayed()
        assertEquals(listOf<PairEdit>(PairEdit.NewPair), edits)
    }

    @Test
    fun inARightToLeftLayoutTheFirstAppIsOnTheRight() {
        show(result(listOf(pair("a", notes, maps))), LayoutDirection.Rtl)

        val first = composeRule.onNodeWithText("Notes").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Maps").fetchSemanticsNode().boundsInRoot
        assertTrue(first.center.x > second.center.x)
    }

    @Test
    fun inALeftToRightLayoutTheFirstAppIsOnTheLeft() {
        show(result(listOf(pair("a", notes, maps))))

        val first = composeRule.onNodeWithText("Notes").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Maps").fetchSemanticsNode().boundsInRoot
        assertTrue(first.center.x < second.center.x)
    }
}

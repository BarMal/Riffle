package com.riffle.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.STREAM_LIST_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.workspace.SourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Stream's behaviour: Show as and Undo, every state's way out, the media transport and the notification reply. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var layout by mutableStateOf(StreamLayout.DEFAULT)
    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()
    private val accessRequests = mutableListOf<List<com.riffle.core.domain.launcher.workspace.SourceId>>()
    private val enabled = mutableListOf<StreamSection>()

    private fun show(results: Map<StreamSection, StreamSectionResult> = defaultResults()) {
        composeRule.setContent {
            ScreenshotBackdrop {
                StreamSurface(
                    layout = layout,
                    results = results,
                    env = StreamFixtures.environment(),
                    callbacks =
                        StreamCallbacks(
                            onAction = { content, action -> actions += content to action },
                            onLayoutAction = { action -> layout = StreamLayoutReducer.reduce(layout, action) },
                            onRequestAccess = { sources -> accessRequests += sources },
                            onEnableSection = { section -> enabled += section },
                        ),
                    contentPadding = PaddingValues(),
                )
            }
        }
    }

    private fun defaultResults() = StreamSection.entries.associateWith { StreamFixtures.ready(it) }

    /** The section list is lazy: bring the node into composition, then return it. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNodeWithTag(STREAM_LIST_TEST_TAG).performScrollToNode(matcher)
    }

    private fun optionsButton(section: String): SemanticsNodeInteraction {
        scrollTo(hasContentDescription("$section options"))
        return composeRule.onNodeWithContentDescription("$section options")
    }

    @Test
    fun showAsRedrawsTheSectionAtOnceAndUndoRestoresIt() {
        show()
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.NOTIFICATIONS))

        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("List").performClick()

        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.NOTIFICATIONS))
        composeRule.onNodeWithText("Notifications shown as List").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun longPressOnASectionHeaderOpensTheSameMenu() {
        show()
        scrollTo(hasText("Agenda"))
        composeRule.onAllNodesWithText("Agenda").onFirst().performTouchInput { longClick() }
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        composeRule.onNodeWithText("Stack").performClick()
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.AGENDA))
    }

    @Test
    fun choosingTheViewAlreadyShownChangesNothing() {
        show()
        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("Stack").performClick()
        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun aSectionCanBeMovedHiddenAndShownAgainWithoutAGesture() {
        show()
        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("Move up").performClick()
        assertEquals(StreamSection.NOTIFICATIONS, layout.sections.first().section)

        optionsButton("Feeds").performClick()
        composeRule.onNodeWithText("Hide section").performClick()
        assertTrue(StreamSection.FEEDS !in layout.enabledSections.map { it.section })
        composeRule.onNodeWithText("Feeds hidden").assertIsDisplayed()
        scrollTo(hasText("Show Feeds"))
        // The Undo snackbar sits over the end of the list for a while; the action itself is what is under test.
        composeRule.onNodeWithText("Show Feeds").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(layout.layoutOf(StreamSection.FEEDS).enabled)
    }

    @Test
    fun aSectionWithoutAccessOffersTheExistingAccessFlow() {
        show(
            defaultResults() +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS))
                ),
        )
        scrollTo(hasText("Allow access to show this"))
        composeRule.onNodeWithText("Allow access to show this").assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").performClick()
        assertEquals(listOf(listOf(SourceIds.NOTIFICATIONS)), accessRequests)
    }

    @Test
    fun aTurnedOffSectionCanBeTurnedBackOnByTheUser() {
        show(defaultResults() + (StreamSection.FEEDS to StreamSectionResult.Off))
        scrollTo(hasText("Turn on"))
        composeRule.onNodeWithText("Turn on").performClick()
        assertEquals(listOf(StreamSection.FEEDS), enabled)
    }

    @Test
    fun tasksSaySoWhenNoAccountIsConnected() {
        show(defaultResults() + (StreamSection.TASKS to StreamSectionResult.NotConnected))
        scrollTo(hasText("No to-do account is connected, so there are no tasks to show yet."))
        composeRule.onNodeWithText("No to-do account is connected, so there are no tasks to show yet.")
            .assertIsDisplayed()
    }

    @Test
    fun theMediaTransportAndScrubberActThroughTheGateway() {
        show()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithContentDescription("Previous track").performClick()

        assertEquals(
            listOf(StreamAction.Pause, StreamAction.Next, StreamAction.Previous),
            actions.map { it.second },
        )
        assertTrue(actions.all { it.first.id == StreamFixtures.media.id })
        composeRule.onNode(hasContentDescription("Playback position")).assertIsDisplayed()
    }

    @Test
    fun theQueueExpandsAndCollapses() {
        show()
        composeRule.onNodeWithContentDescription("Show queue").performClick()
        composeRule.onNodeWithText("Reunion").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hide queue").performClick()
        composeRule.onAllNodesWithText("Reunion").assertCountEquals(0)
    }

    @Test
    fun aNotificationExpandsToItsThreadAndSendsAReply() {
        show()
        // The stack composes the focused card and its neighbours; the focused one is the leftmost.
        val buttons = composeRule.onAllNodesWithText("Reply")
        val focused = buttons.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.left }.index
        buttons[focused].performClick()

        composeRule.onNodeWithText("Sam: Morning! Got a minute?").assertIsDisplayed()
        composeRule.onNodeWithText("Write a reply").performTextInput("On my way")
        composeRule.onNodeWithContentDescription("Send reply").performClick()

        val reply = actions.single { it.second is StreamAction.Reply }
        assertEquals(StreamFixtures.chatNotification.id, reply.first.id)
        assertEquals(StreamAction.Reply("On my way"), reply.second)
    }

    @Test
    fun theNextCardIsReachableWithoutASwipe() {
        show()
        // Two notification stacks and the Now stack: the first stack's own "next" button.
        composeRule.onAllNodesWithContentDescription("Next card").onFirst().performClick()
        composeRule.onNodeWithText("Card 2 of 3").assertIsDisplayed()
    }

    @Test
    fun theFocusedCardExposesItsStackAsAccessibilityActions() {
        show()
        val labels =
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
                .fetchSemanticsNodes()
                .flatMap { it.config.getOrElse(SemanticsActions.CustomActions) { emptyList() } }
                .map { it.label }
        assertTrue("Next card" in labels && "Previous card" in labels && "Open card" in labels)
    }

    @Test
    fun everyTargetIsAtLeast48dp() {
        show()
        val minTarget = with(composeRule.density) { 48.dp.roundToPx() }
        val tooSmall =
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .fetchSemanticsNodes()
                .filter { node ->
                    val role = node.config.getOrElseNullable(SemanticsProperties.Role) { null }
                    role != null && (node.size.height < minTarget - 1 || node.size.width < minTarget - 1)
                }
        // Sliders and checkboxes size themselves; everything else with a role is a 48 dp button.
        assertTrue(
            "Targets under 48 dp (px): ${tooSmall.map { it.size.toString() + " " + it.config }}",
            tooSmall.isEmpty(),
        )
    }
}

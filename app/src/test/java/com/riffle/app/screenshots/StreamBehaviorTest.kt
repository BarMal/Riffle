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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.STREAM_OPTIONS_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_SPINE_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_STAGE_MAIN_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_STAGE_RAIL_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamFocusRequest
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.streamSpineMarkerTestTag
import com.riffle.app.launcher.stream.streamStageThumbTestTag
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.StreamWideView
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
 * The Stream's behaviour as pages: landing, the pager and the spine, Show as and Undo, every state's way out, the
 * media transport and the notification reply, every callback reaching the page of every section, page stability while
 * content comes and goes, and on an expanded posture the two-up pager and the Stage view.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var layout by mutableStateOf(StreamLayout.DEFAULT)
    private var results by mutableStateOf(defaultResults())
    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()
    private val accessRequests = mutableListOf<List<SourceId>>()
    private val enabled = mutableListOf<StreamSection>()
    private val added = mutableListOf<String>()
    private val pageChanges = mutableListOf<String>()
    private var refreshes = 0
    private var connects = 0

    private val handledFocus = mutableListOf<Long>()

    private fun show(
        initialResults: Map<StreamSection, StreamSectionResult> = defaultResults(),
        focus: StreamFocusRequest? = null,
        initialPageKey: String? = null,
        wide: Boolean = false,
    ) {
        results = initialResults
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
                            onAddTask = { text -> added += text },
                            onRefreshTasks = { refreshes++ },
                            onConnectTasks = { connects++ },
                        ),
                    contentPadding = PaddingValues(),
                    focusRequest = focus,
                    onFocusHandled = { id -> handledFocus += id },
                    initialPageKey = initialPageKey,
                    onPageChanged = { key -> pageChanges += key },
                    wideLayout = wide,
                )
            }
        }
    }

    private fun defaultResults(): Map<StreamSection, StreamSectionResult> =
        StreamSection.entries.associateWith { StreamFixtures.ready(it) }

    private fun marker(key: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(streamSpineMarkerTestTag(key)).also { it.performScrollTo() }

    private fun goTo(key: String) {
        marker(key).performClick()
        composeRule.waitForIdle()
    }

    private fun optionsButton(section: String): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription("$section options")

    private fun customActionLabels(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsActions.CustomActions) { emptyList() } }
            .map { it.label }

    // Landing ------------------------------------------------------------------------------------------------------

    @Test
    fun landingOnAnAppsStackLandsOnItsPageAndIsHandledOnce() {
        show(focus = StreamFocusRequest(7, StreamFocusTarget.AppStack("mail:personal")))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        marker(StreamPageKeys.MAIL).assertIsSelected()
        assertEquals(listOf(7L), handledFocus)
    }

    @Test
    fun landingOnNowIsHandledWithoutWaitingForNotifications() {
        show(
            defaultResults() + (StreamSection.NOTIFICATIONS to StreamSectionResult.Loading),
            focus = StreamFocusRequest(8, StreamFocusTarget.Now),
        )
        composeRule.waitForIdle()

        marker(StreamPageKeys.NOW).assertIsSelected()
        assertEquals(listOf(8L), handledFocus)
    }

    @Test
    fun aGlanceWidgetLandsOnItsSectionsPage() {
        show(focus = StreamFocusRequest(11, StreamFocusTarget.Section(StreamSection.AGENDA)))
        composeRule.waitForIdle()

        marker(StreamPageKeys.AGENDA).assertIsSelected()
        assertEquals(listOf(11L), handledFocus)
    }

    @Test
    fun landingOnAStackThatHasGoneStillLandsOnTheFirstNotificationsPage() {
        show(focus = StreamFocusRequest(9, StreamFocusTarget.AppStack("gone:personal")))
        composeRule.waitForIdle()

        marker(StreamPageKeys.CHAT).assertIsSelected()
        assertEquals(listOf(9L), handledFocus)
    }

    @Test
    fun landingOnAStackWaitsForTheNotificationsToArriveThenLandsOnIt() {
        show(
            defaultResults() + (StreamSection.NOTIFICATIONS to StreamSectionResult.Loading),
            focus = StreamFocusRequest(10, StreamFocusTarget.AppStack("mail:personal")),
        )
        composeRule.waitForIdle()
        assertTrue(handledFocus.isEmpty())

        results = defaultResults()
        composeRule.waitForIdle()

        marker(StreamPageKeys.MAIL).assertIsSelected()
        assertEquals(listOf(10L), handledFocus)
    }

    @Test
    fun theLastPageIsRememberedAndReturnedTo() {
        show(initialPageKey = StreamPageKeys.TASKS)
        composeRule.waitForIdle()
        marker(StreamPageKeys.TASKS).assertIsSelected()

        goTo(StreamPageKeys.AGENDA)
        assertEquals(StreamPageKeys.AGENDA, pageChanges.last())
    }

    @Test
    fun aRememberedAppPageIsFoundOnceItsNotificationsHaveArrived() {
        show(
            defaultResults() + (StreamSection.NOTIFICATIONS to StreamSectionResult.Loading),
            initialPageKey = StreamPageKeys.MAIL,
        )
        composeRule.waitForIdle()
        assertTrue("nothing is remembered over while the page cannot exist yet", pageChanges.isEmpty())

        results = defaultResults()
        composeRule.waitForIdle()

        marker(StreamPageKeys.MAIL).assertIsSelected()
        assertEquals(StreamPageKeys.MAIL, pageChanges.last())
    }

    // The pager and the spine --------------------------------------------------------------------------------------

    @Test
    fun theSpineHasOneTabPerPageAndTheCurrentOneIsSelected() {
        show()
        val tabs =
            composeRule.onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab),
            )
        // Now, two notification apps, Recents, Agenda, Tasks and two feeds.
        tabs.assertCountEquals(8)
        marker(StreamPageKeys.NOW).assertIsSelected()
        marker(StreamPageKeys.CHAT).assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Chat, page 2 of 8").assertExists()
        composeRule.onNodeWithTag(STREAM_SPINE_TEST_TAG).assertExists()
    }

    @Test
    fun tappingAMarkerJumpsToItsPage() {
        show()
        goTo(StreamPageKeys.MAIL)
        marker(StreamPageKeys.MAIL).assertIsSelected()
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        goTo(StreamPageKeys.FEED_B)
        composeRule.onNodeWithText("Notes from the desktop shell").assertIsDisplayed()
    }

    @Test
    fun swipingSidewaysChangesThePageAndTheSpineFollows() {
        show()
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        marker(StreamPageKeys.CHAT).assertIsSelected()
        composeRule.onNodeWithText("Are we still on for lunch tomorrow?").assertIsDisplayed()
    }

    @Test
    fun everySwipeHasAnActionAndThePagerKeepsOneList() {
        show()
        val pagerActions =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Previous page", "Next page"), pagerActions.map { it.label })
        composeRule.runOnUiThread { pagerActions.first { it.label == "Next page" }.action() }
        composeRule.waitForIdle()
        marker(StreamPageKeys.CHAT).assertIsSelected()
        composeRule.runOnUiThread {
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Previous page" }.action()
        }
        composeRule.waitForIdle()
        marker(StreamPageKeys.NOW).assertIsSelected()
    }

    @Test
    fun eachPageIsALabelledPane() {
        show(initialPageKey = StreamPageKeys.MAIL)
        composeRule.waitForIdle()
        val panes =
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
                .fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.PaneTitle] }
        assertTrue("Mail" in panes)
    }

    @Test
    fun aNewNotificationDoesNotJumpThePager() {
        show(initialPageKey = StreamPageKeys.MAIL)
        composeRule.waitForIdle()
        marker(StreamPageKeys.MAIL).assertIsSelected()

        // A third app posts: its page is the newest, so it leads the Notifications pages.
        val ready = StreamFixtures.ready(StreamSection.NOTIFICATIONS)
        val newest =
            StreamGroup(
                "maps:personal",
                "Maps",
                listOf(
                    StreamFixtures.chatNotification.copy(
                        id = com.riffle.core.domain.launcher.stream.StreamContentId("notifications:maps:1"),
                        title = "Arrived",
                        text = "You have arrived.",
                    ),
                ),
            )
        results = results + (StreamSection.NOTIFICATIONS to StreamSectionResult.Ready(listOf(newest) + ready.groups))
        composeRule.waitForIdle()

        marker(StreamPageKeys.MAIL).assertIsSelected()
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Maps, page 2 of 9").assertCountEquals(1)
    }

    @Test
    fun whenTheCurrentPageGoesAwayTheNearestTakesOver() {
        show(initialPageKey = StreamPageKeys.MAIL)
        composeRule.waitForIdle()

        // Mail's notifications are cleared: Chat is before it, Recents after it, and the pager moves on to Recents.
        val chatOnly = StreamSectionResult.Ready(StreamFixtures.ready(StreamSection.NOTIFICATIONS).groups.take(1))
        results = results + (StreamSection.NOTIFICATIONS to chatOnly)
        composeRule.waitForIdle()

        marker(StreamPageKeys.RECENTS).assertIsSelected()
    }

    @Test
    fun noNotificationsIsOneEmptyPage() {
        show(defaultResults() + (StreamSection.NOTIFICATIONS to StreamSectionResult.Empty))
        goTo("notifications")
        composeRule.onNodeWithText("No notifications.").assertIsDisplayed()
    }

    // Show as, Move, Hide ------------------------------------------------------------------------------------------

    @Test
    fun showAsRedrawsTheWholeSectionAtOnceAndUndoRestoresIt() {
        show(initialPageKey = StreamPageKeys.CHAT)
        composeRule.waitForIdle()
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.NOTIFICATIONS))

        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("List").performClick()

        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.NOTIFICATIONS))
        composeRule.onNodeWithText("Notifications shown as List").assertIsDisplayed()
        // The page is now rows, not a pile of cards.
        composeRule.onAllNodesWithText("Card 1 of 2").assertCountEquals(0)
        // And the section's other page is a list too.
        goTo(StreamPageKeys.MAIL)
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        composeRule.onAllNodesWithText("Card 1 of 1").assertCountEquals(0)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun longPressOnAPageHeaderOpensTheSameMenu() {
        show(initialPageKey = StreamPageKeys.AGENDA)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Agenda").onFirst().performTouchInput { longClick() }
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        composeRule.onNodeWithText("Stack").performClick()
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.AGENDA))
    }

    @Test
    fun choosingTheViewAlreadyShownChangesNothing() {
        show(initialPageKey = StreamPageKeys.CHAT)
        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("Stack").performClick()
        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun aSectionCanBeMovedHiddenAndShownAgainWithoutAGesture() {
        show(initialPageKey = StreamPageKeys.CHAT)
        optionsButton("Notifications").performClick()
        composeRule.onNodeWithText("Move up").performClick()
        assertEquals(StreamSection.NOTIFICATIONS, layout.sections.first().section)

        goTo(StreamPageKeys.FEED_A)
        optionsButton("Feeds").performClick()
        composeRule.onNodeWithText("Hide section").performClick()
        assertTrue(StreamSection.FEEDS !in layout.enabledSections.map { it.section })
        composeRule.onNodeWithText("Feeds hidden").assertIsDisplayed()

        // The Stream's own menu offers the hidden section back.
        composeRule.onNodeWithTag(STREAM_OPTIONS_TEST_TAG).performClick()
        composeRule.onNodeWithText("Show Feeds").performClick()
        assertTrue(layout.layoutOf(StreamSection.FEEDS).enabled)
    }

    @Test
    fun hidingEverySectionLeavesAWayBack() {
        layout =
            StreamSection.entries.fold(StreamLayout.DEFAULT) { acc, section ->
                StreamLayoutReducer.reduce(acc, StreamLayoutAction.SetEnabled(section, false))
            }
        show()
        composeRule.onNodeWithText("Every section is hidden. Show one to fill the Stream again.").assertIsDisplayed()
        composeRule.onNodeWithText("Show Tasks").performClick()
        assertTrue(layout.layoutOf(StreamSection.TASKS).enabled)
    }

    // States -------------------------------------------------------------------------------------------------------

    @Test
    fun aSectionWithoutAccessOffersTheExistingAccessFlow() {
        show(
            defaultResults() +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS))
                ),
            initialPageKey = "notifications",
        )
        composeRule.onNodeWithText("Allow access to show this").assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").performClick()
        assertEquals(listOf(listOf(SourceIds.NOTIFICATIONS)), accessRequests)
    }

    @Test
    fun aTurnedOffSectionCanBeTurnedBackOnByTheUser() {
        show(defaultResults() + (StreamSection.FEEDS to StreamSectionResult.Off), initialPageKey = "feeds")
        composeRule.onNodeWithText("Turn on").performClick()
        assertEquals(listOf(StreamSection.FEEDS), enabled)
    }

    @Test
    fun tasksOfferToConnectWhenNoAccountIsConnected() {
        show(defaultResults() + (StreamSection.TASKS to StreamSectionResult.NotConnected()), initialPageKey = "tasks")
        composeRule.onNodeWithText("Todoist is not connected, so there are no tasks to show.").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Todoist").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun aRejectedTokenSaysSoAndOffersToReplaceIt() {
        show(
            defaultResults() +
                (
                    StreamSection.TASKS to
                        StreamSectionResult.NotConnected(
                            com.riffle.core.domain.launcher.stream.NotConnectedReason.TOKEN_REJECTED,
                        )
                ),
            initialPageKey = "tasks",
        )
        composeRule.onNodeWithText("Todoist did not accept the saved token. Paste a new one in Settings.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Replace token").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun tickingATaskCompletesItAndTheTitleOpensIt() {
        show(initialPageKey = StreamPageKeys.TASKS)
        composeRule.onNodeWithContentDescription("Mark Send the invoice done").performClick()
        composeRule.onNodeWithText("Send the invoice").performClick()
        assertEquals(listOf(StreamAction.Complete, StreamAction.Open), actions.map { it.second })
        assertTrue(actions.all { it.first.id.value == "todoist:2" })
    }

    @Test
    fun aHiddenTaskCannotBeTicked() {
        val hidden =
            StreamFixtures.tasks.map {
                it.copy(title = null, listName = null, privacy = StreamPrivacy.REDACTED, actions = emptySet())
            }
        show(
            defaultResults() +
                (
                    StreamSection.TASKS to
                        StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.TASKS, null, hidden)))
                ),
            initialPageKey = StreamPageKeys.TASKS,
        )
        composeRule.onAllNodesWithText("Hidden content").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Renew passport").assertDoesNotExist()
    }

    @Test
    fun quickAddSendsTheTextAndClearsTheField() {
        show(initialPageKey = StreamPageKeys.TASKS)
        composeRule.onNodeWithTag("stream-tasks-quick-add").performTextInput("Buy milk")
        composeRule.onNodeWithTag("stream-tasks-add").performClick()
        assertEquals(listOf("Buy milk"), added)
        composeRule.onNodeWithText("Buy milk").assertDoesNotExist()
    }

    @Test
    fun aStaleListSaysSoAndRetryRefreshes() {
        val stale =
            StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.TASKS, null, StreamFixtures.staleTasks)))
        show(defaultResults() + (StreamSection.TASKS to stale), initialPageKey = StreamPageKeys.TASKS)
        composeRule.onNodeWithText("Can't reach Todoist. Showing the tasks from the last refresh.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun withNoDataAndNoConnectionTasksSayTheyCannotReachTodoist() {
        show(defaultResults() + (StreamSection.TASKS to StreamSectionResult.Unavailable), initialPageKey = "tasks")
        composeRule.onNodeWithText("Can't reach Todoist, and there are no saved tasks to show.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshes)
    }

    // Cards --------------------------------------------------------------------------------------------------------

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
        show(initialPageKey = StreamPageKeys.CHAT)
        composeRule.onAllNodesWithText("Reply").onFirst().performClick()

        composeRule.onNodeWithText("Sam: Morning! Got a minute?").assertIsDisplayed()
        composeRule.onNodeWithText("Write a reply").performTextInput("On my way")
        composeRule.onNodeWithContentDescription("Send reply").performClick()

        val reply = actions.single { it.second is StreamAction.Reply }
        assertEquals(StreamFixtures.chatNotification.id, reply.first.id)
        assertEquals(StreamAction.Reply("On my way"), reply.second)
    }

    @Test
    fun theNextCardIsReachableWithoutASwipe() {
        show(initialPageKey = StreamPageKeys.CHAT)
        composeRule.onNodeWithContentDescription("Next card").performClick()
        composeRule.onNodeWithText("Card 2 of 2").assertIsDisplayed()
    }

    @Test
    fun theFocusedCardExposesItsStackAsAccessibilityActions() {
        show(initialPageKey = StreamPageKeys.CHAT)
        val labels = customActionLabels()
        assertTrue("Next card" in labels && "Previous card" in labels && "Open card" in labels)
    }

    @Test
    fun theNextCardsPeekOutBelowTheFocusedOneNotBesideIt() {
        show(initialPageKey = StreamPageKeys.CHAT)
        composeRule.waitForIdle()
        // The second notification is a strip below the first: its title is on screen, lower than the focused card's.
        val focused = composeRule.onNodeWithText("Sam").fetchSemanticsNode().boundsInRoot
        val peek = composeRule.onNodeWithText("Priya").fetchSemanticsNode().boundsInRoot
        assertTrue("the peeking card is below the focused one", peek.top > focused.bottom)
        assertTrue("and in the same column", kotlin.math.abs(peek.left - focused.left) < focused.width)
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

    // Callbacks reach every section's page -------------------------------------------------------------------------

    private fun assertActionOn(
        page: String,
        control: () -> Unit,
        expected: StreamAction,
    ) {
        actions.clear()
        goTo(page)
        control()
        composeRule.waitForIdle()
        assertEquals("a control on $page", listOf(expected), actions.map { it.second })
    }

    @Test
    fun aControlOnEverySectionsPageReachesTheHost() {
        show()
        assertActionOn(StreamPageKeys.NOW, {
            composeRule.onNodeWithContentDescription("Pause").performClick()
        }, StreamAction.Pause)
        assertActionOn(StreamPageKeys.CHAT, {
            composeRule.onAllNodesWithText("Dismiss").onFirst().performClick()
        }, StreamAction.Dismiss)
        assertActionOn(
            StreamPageKeys.MAIL,
            { composeRule.onNodeWithText("Dismiss").performClick() },
            StreamAction.Dismiss,
        )
        assertActionOn(StreamPageKeys.RECENTS, {
            composeRule.onAllNodesWithText("Chat").onFirst().performClick()
        }, StreamAction.Open)
        assertActionOn(StreamPageKeys.AGENDA, {
            composeRule.onNodeWithText("Design standup").performClick()
        }, StreamAction.Open)
        assertActionOn(
            StreamPageKeys.TASKS,
            { composeRule.onNodeWithContentDescription("Mark Send the invoice done").performClick() },
            StreamAction.Complete,
        )
        assertActionOn(StreamPageKeys.FEED_A, {
            composeRule.onNodeWithText("A new look for the launcher: glass, everywhere").performClick()
        }, StreamAction.Open)
        assertActionOn(StreamPageKeys.FEED_B, {
            composeRule.onNodeWithText("Notes from the desktop shell").performClick()
        }, StreamAction.Open)
        // The layout, add-task, refresh, access, enable and connect callbacks are covered by their own tests above; a
        // tap on the Tasks page's quick-add proves the callbacks the surface rebuilt are still the host's.
        goTo(StreamPageKeys.TASKS)
        composeRule.onNodeWithTag("stream-tasks-quick-add").performTextInput("Water the ferns")
        composeRule.onNodeWithTag("stream-tasks-add").performClick()
        assertEquals(listOf("Water the ferns"), added)
    }

    @Test
    fun theSameControlsWorkInEveryPageInTheListView() {
        layout =
            StreamSection.entries.fold(StreamLayout.DEFAULT) { acc, section ->
                StreamLayoutReducer.reduce(acc, StreamLayoutAction.SetView(section, StreamView.LIST))
            }
        show()
        goTo(StreamPageKeys.TASKS)
        composeRule.onNodeWithContentDescription("Mark Send the invoice done").performClick()
        goTo(StreamPageKeys.CHAT)
        composeRule.onAllNodesWithContentDescription("Dismiss").onFirst().performClick()
        assertEquals(listOf(StreamAction.Complete, StreamAction.Dismiss), actions.map { it.second })
    }

    // Unfolded: two pages and the Stage ----------------------------------------------------------------------------

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun onAWideScreenTwoPagesShareTheWidth() {
        show(initialPageKey = StreamPageKeys.CHAT, wide = true)
        composeRule.waitForIdle()
        // Chat and the page after it (Mail) are both on screen at once.
        composeRule.onNodeWithText("Are we still on for lunch tomorrow?").assertIsDisplayed()
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
    }

    @Test
    fun onAPhoneOnePageShowsEvenWhenWideIsAsked() {
        show(initialPageKey = StreamPageKeys.CHAT, wide = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Are we still on for lunch tomorrow?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Invoice 2026-114").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun theStageShowsOneStageLargeAndARailOfTheOthers() {
        layout = StreamLayout(wideView = StreamWideView.STAGE)
        show(initialPageKey = StreamPageKeys.CHAT, wide = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_STAGE_MAIN_TEST_TAG).assertIsDisplayed()
        // The selected stage is not in the rail; every other page is.
        composeRule.onAllNodesWithText("Are we still on for lunch tomorrow?").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.CHAT)).assertDoesNotExist()
        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.MAIL)).assertIsDisplayed()
        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.NOW)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun tappingARailStageBringsItToTheCentreAndSendsTheOtherBack() {
        layout = StreamLayout(wideView = StreamWideView.STAGE)
        show(initialPageKey = StreamPageKeys.CHAT, wide = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.MAIL)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.MAIL)).assertDoesNotExist()
        composeRule.onNodeWithTag(streamStageThumbTestTag(StreamPageKeys.CHAT)).assertIsDisplayed()
        // The stage just left is first in the rail.
        val rail = composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).fetchSemanticsNode()
        assertTrue(rail.children.isNotEmpty())
        assertEquals(StreamPageKeys.MAIL, pageChanges.last())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun theStageAndItsMainStackStillTakeActions() {
        layout = StreamLayout(wideView = StreamWideView.STAGE)
        show(initialPageKey = StreamPageKeys.NOW, wide = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertEquals(listOf(StreamAction.Pause), actions.map { it.second })
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun theStreamsMenuShowsAsPagesOrStageAndUndoes() {
        show(wide = true)
        composeRule.onNodeWithTag(STREAM_OPTIONS_TEST_TAG).performClick()
        composeRule.onNodeWithText("Stage").performClick()
        assertEquals(StreamWideView.STAGE, layout.wideView)
        composeRule.onNodeWithText("Stream shown as Stage").assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamWideView.PAGES, layout.wideView)
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aPhoneHasNoWideChoice() {
        show(wide = false)
        composeRule.onAllNodesWithContentDescription("Stream options").assertCountEquals(0)
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, STREAM_OPTIONS_TEST_TAG),
        ).assertCountEquals(0)
    }
}

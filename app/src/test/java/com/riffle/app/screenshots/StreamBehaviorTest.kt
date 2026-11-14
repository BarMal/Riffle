package com.riffle.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.STREAM_OPTIONS_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_STAGE_MAIN_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_STAGE_RAIL_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamFocusRequest
import com.riffle.app.launcher.stream.StreamPageOption
import com.riffle.app.launcher.stream.StreamSurface
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
 * The Stream's behaviour as pages: landing, the pager in the Dock's order and what it tells the Dock (the open
 * page), the
 * page options the Dock's tiles ask for with their Undo, every state's way out, the
 * media transport and the notification reply, every callback reaching the page of every section, page stability while
 * content comes and goes, and on an expanded posture the two-up pager and the Stage view.
 */
@Suppress("LargeClass")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamBehaviorTest {
    private companion object {
        const val MAX_PAGE_STEPS = 12
    }

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

    /** What the Stream tells the Dock's contextual mode: its pages in the Dock's order and the open one. */
    private val dock = StreamDockLink()

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
                    dockLink = dock,
                )
            }
        }
    }

    private fun defaultResults(): Map<StreamSection, StreamSectionResult> =
        StreamSection.entries.associateWith { StreamFixtures.ready(it) }

    /** The page in front is [key]: what the Dock rings. */
    private fun assertOpen(key: String) {
        composeRule.waitForIdle()
        assertEquals(key, dock.currentKey)
    }

    /** Brings [key] to the front the way a screen reader does: the pager's Next and Previous page actions. */
    private fun goTo(key: String) {
        repeat(MAX_PAGE_STEPS) {
            composeRule.waitForIdle()
            val keys = dock.pages.map { it.key }
            val to = keys.indexOf(key)
            check(to >= 0) { "$key is not a page: $keys" }
            val from = keys.indexOf(dock.currentKey)
            if (from == to) return
            val label = if (to > from) "Next page" else "Previous page"
            composeRule.runOnUiThread {
                composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions].first { it.label == label }.action()
            }
        }
        error("never reached $key")
    }

    /** What a long-press of a Dock tile asks of the Stream (ADR 0003, Amendment 3), asked the way the Dock asks it. */
    private fun option(option: StreamPageOption) {
        composeRule.runOnUiThread { dock.requestOption(option) }
        composeRule.waitForIdle()
    }

    private val aCardPosition =
        SemanticsMatcher("a card position") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card ") == true
        }

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
        assertOpen(StreamPageKeys.MAIL)
        assertEquals(listOf(7L), handledFocus)
    }

    @Test
    fun landingOnNowIsHandledWithoutWaitingForNotifications() {
        show(
            defaultResults() + (StreamSection.NOTIFICATIONS to StreamSectionResult.Loading),
            focus = StreamFocusRequest(8, StreamFocusTarget.Now),
        )
        composeRule.waitForIdle()

        assertOpen(StreamPageKeys.NOW)
        assertEquals(listOf(8L), handledFocus)
    }

    @Test
    fun aGlanceWidgetLandsOnItsSectionsPage() {
        show(focus = StreamFocusRequest(11, StreamFocusTarget.Section(StreamSection.AGENDA)))
        composeRule.waitForIdle()

        assertOpen(StreamPageKeys.AGENDA)
        assertEquals(listOf(11L), handledFocus)
    }

    @Test
    fun landingOnAStackThatHasGoneStillLandsOnTheFirstNotificationsPage() {
        show(focus = StreamFocusRequest(9, StreamFocusTarget.AppStack("gone:personal")))
        composeRule.waitForIdle()

        assertOpen(StreamPageKeys.CHAT)
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

        assertOpen(StreamPageKeys.MAIL)
        assertEquals(listOf(10L), handledFocus)
    }

    @Test
    fun theLastPageIsRememberedAndReturnedTo() {
        show(initialPageKey = StreamPageKeys.TASKS)
        composeRule.waitForIdle()
        assertOpen(StreamPageKeys.TASKS)

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

        assertOpen(StreamPageKeys.MAIL)
        assertEquals(StreamPageKeys.MAIL, pageChanges.last())
    }

    // The pager, in the Dock's order ---------------------------------------------------------------------------------

    @Test
    fun thePagesFollowTheDocksOrderAndTheFirstIsOpen() {
        show()
        composeRule.waitForIdle()
        // The Dock's live apps (newest first), now playing, then the built-in pages in the layout's order; a feed's
        // pages stay together.
        assertEquals(
            listOf(
                StreamPageKeys.CHAT,
                StreamPageKeys.MAIL,
                StreamPageKeys.NOW,
                StreamPageKeys.RECENTS,
                StreamPageKeys.AGENDA,
                StreamPageKeys.TASKS,
                StreamPageKeys.FEED_A,
                StreamPageKeys.FEED_B,
            ),
            dock.pages.map { it.key },
        )
        assertOpen(StreamPageKeys.CHAT)
    }

    @Test
    fun theLiveAppSlotsDecideWhichAppPagesLeadAndTheOthersFollowThem() {
        results = defaultResults()
        composeRule.setContent {
            ScreenshotBackdrop {
                StreamSurface(
                    layout = layout,
                    results = results,
                    env = StreamFixtures.environment(),
                    callbacks = StreamCallbacks(),
                    contentPadding = PaddingValues(),
                    liveAppSlots = 1,
                    dockLink = dock,
                )
            }
        }
        composeRule.waitForIdle()
        // One live app on the Dock (the newest); the other app's page keeps its place right behind it.
        assertEquals(
            listOf(StreamPageKeys.CHAT, StreamPageKeys.MAIL, StreamPageKeys.NOW),
            dock.pages.map { it.key }.take(3),
        )
    }

    @Test
    fun swipingSidewaysChangesThePageAndTellsTheDock() {
        show()
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertOpen(StreamPageKeys.MAIL)
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
    }

    @Test
    fun thereIsNoSpineAndNoPageHeader() {
        show()
        composeRule.waitForIdle()
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab),
        ).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Chat options").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Notifications options").assertCountEquals(0)
    }

    @Test
    fun everySwipeHasAnActionAndThePagerKeepsOneList() {
        show(initialPageKey = StreamPageKeys.NOW)
        val pagerActions =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Previous page", "Next page"), pagerActions.map { it.label })
        composeRule.runOnUiThread { pagerActions.first { it.label == "Next page" }.action() }
        composeRule.waitForIdle()
        assertOpen(StreamPageKeys.RECENTS)
        composeRule.runOnUiThread {
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Previous page" }.action()
        }
        composeRule.waitForIdle()
        assertOpen(StreamPageKeys.NOW)
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
        assertOpen(StreamPageKeys.MAIL)

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
                        postedAtEpochMillis = StreamFixtures.NOW,
                    ),
                ),
            )
        results = results + (StreamSection.NOTIFICATIONS to StreamSectionResult.Ready(listOf(newest) + ready.groups))
        composeRule.waitForIdle()

        assertOpen(StreamPageKeys.MAIL)
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        // The new app is the Dock's first live app, so its page leads the pager too.
        assertEquals("notifications/maps:personal", dock.pages.first().key)
        assertEquals(9, dock.pages.size)
    }

    @Test
    fun whenTheCurrentPageGoesAwayTheNearestTakesOver() {
        show(initialPageKey = StreamPageKeys.MAIL)
        composeRule.waitForIdle()

        // Mail's notifications are cleared: Chat is before it, Now after it, and the pager moves on to Now.
        val chatOnly = StreamSectionResult.Ready(StreamFixtures.ready(StreamSection.NOTIFICATIONS).groups.take(1))
        results = results + (StreamSection.NOTIFICATIONS to chatOnly)
        composeRule.waitForIdle()

        assertOpen(StreamPageKeys.NOW)
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

        option(StreamPageOption.ShowAs(StreamSection.NOTIFICATIONS, StreamView.LIST))

        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.NOTIFICATIONS))
        composeRule.onNodeWithText("Notifications shown as List").assertIsDisplayed()
        // The page is now rows, not a pile of cards.
        composeRule.onAllNodes(aCardPosition).assertCountEquals(0)
        // And the section's other page is a list too.
        goTo(StreamPageKeys.MAIL)
        composeRule.onNodeWithText("Invoice 2026-114").assertIsDisplayed()
        composeRule.onAllNodes(aCardPosition).assertCountEquals(0)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun choosingTheViewAlreadyShownChangesNothing() {
        show(initialPageKey = StreamPageKeys.CHAT)
        option(StreamPageOption.ShowAs(StreamSection.NOTIFICATIONS, StreamView.STACK))
        assertEquals(StreamLayout.DEFAULT, layout)
    }

    @Test
    fun aSectionCanBeMovedHiddenAndShownAgainFromTheDocksTiles() {
        show(initialPageKey = StreamPageKeys.CHAT)
        option(StreamPageOption.Move(StreamSection.NOTIFICATIONS, -1))
        assertEquals(StreamSection.NOTIFICATIONS, layout.sections.first().section)

        goTo(StreamPageKeys.FEED_A)
        option(StreamPageOption.Hide(StreamSection.FEEDS))
        assertTrue(StreamSection.FEEDS !in layout.enabledSections.map { it.section })
        composeRule.onNodeWithText("Feeds hidden").assertIsDisplayed()

        // A hidden section is offered back by the Dock's tiles.
        option(StreamPageOption.Show(StreamSection.FEEDS))
        assertTrue(layout.layoutOf(StreamSection.FEEDS).enabled)
    }

    @Test
    fun anOptionIsAppliedOnceAndTheRequestIsClearedAfterIt() {
        show(initialPageKey = StreamPageKeys.CHAT)
        option(StreamPageOption.Hide(StreamSection.TASKS))
        assertEquals(null, dock.pendingOption)
        // Asking for the same option twice in a row is two requests: the second is a no-op on the layout.
        option(StreamPageOption.Hide(StreamSection.TASKS))
        assertEquals(null, dock.pendingOption)
        assertTrue(StreamSection.TASKS !in layout.enabledSections.map { it.section })
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
        show(initialPageKey = StreamPageKeys.NOW)
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
        show(initialPageKey = StreamPageKeys.NOW)
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
        // The stack's own action (there is no stepper pill): the second card comes to the front.
        val next =
            composeRule.onNode(
                SemanticsMatcher("the stack") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Next card" } == true
                },
            ).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Next card" }
        composeRule.runOnUiThread { next.action() }
        composeRule.waitForIdle()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Card 2 of 2"),
        ).assertExists()
    }

    @Test
    fun theStackAndItsFrontCardExposeTheAccessibilityActions() {
        show(initialPageKey = StreamPageKeys.CHAT)
        val labels = customActionLabels()
        assertTrue("Next card" in labels && "Previous card" in labels && "Open card" in labels)
    }

    @Test
    fun theNextCardsPeekOutAboveTheFocusedOneNotBesideIt() {
        show(initialPageKey = StreamPageKeys.CHAT)
        composeRule.waitForIdle()
        // The stack is anchored to the Dock (dock modes slice 4a): the older notification is a strip above the first.
        // (Positions, not bounds: a node's bounds are clipped by its parents.)
        val focused = composeRule.onNodeWithText("Sam").fetchSemanticsNode()
        val peeks =
            composeRule.onAllNodes(
                SemanticsMatcher("a card slot") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true &&
                        node.config.getOrNull(SemanticsProperties.ContentDescription) != null
                },
            ).fetchSemanticsNodes()
        assertTrue("the second card peeks", peeks.size == 1)
        val peek = peeks.single()
        assertTrue(
            "the peeking card is above the focused one",
            peek.positionInRoot.y + peek.size.height <= focused.positionInRoot.y,
        )
        assertTrue(
            "and in the same column",
            kotlin.math.abs(peek.positionInRoot.x - focused.positionInRoot.x) < focused.size.width,
        )
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
    fun theStagesMenuShowsAsPagesOrStageAndUndoes() {
        // The pager has no bar of its own to hold the Stream's menu any more (Settings > Stream chooses Pages or
        // Stage);
        // the Stage keeps its slim bar and its menu.
        layout = StreamLayout(wideView = StreamWideView.STAGE)
        show(wide = true)
        composeRule.onNodeWithTag(STREAM_OPTIONS_TEST_TAG).performClick()
        composeRule.onNodeWithText("Pages").performClick()
        assertEquals(StreamWideView.PAGES, layout.wideView)
        composeRule.onNodeWithText("Stream shown as Pages").assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertDoesNotExist()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamWideView.STAGE, layout.wideView)
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
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

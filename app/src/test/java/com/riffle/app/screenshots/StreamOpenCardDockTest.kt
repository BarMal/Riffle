package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionQueueEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * An open Stream card with its actions in the Dock (dock modes slice 5; ADR 0003, Amendment 1, "Stream: browse, then
 * open"), through the real shell, Dock and Stream wiring with only the platform services faked: tapping the front card
 * opens it and the Dock gains that card's actions; a reply sent through the Dock's field and a transport button in it
 * reach the action gateway; a press of the Dock and Back each close the card first and keep the Stream; the press is
 * still the Dock's when it starts on the row's empty space.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamOpenCardDockTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "open-card").apply { isDaemon = true } }

    // Actions run off the main thread (as on a device), so the list is written from another thread.
    private val performed = java.util.concurrent.CopyOnWriteArrayList<Pair<StreamContent, StreamAction>>()
    private val sent = mutableListOf<LauncherShellAction>()

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val musicPackage = ScreenshotFixtures.music.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val nowPage = StreamPagePlanner.keyOf(StreamSection.NOW)

    private val gateway =
        object : StreamActionGateway {
            override fun perform(
                content: StreamContent,
                action: StreamAction,
            ): StreamActionResult {
                performed += content to action
                return StreamActionResult.Performed
            }
        }

    private fun chatNotification() =
        LauncherNotification(
            key = LauncherNotificationKey("0|chat|1"),
            packageName = AppPackageName(chatPackage),
            canDismiss = true,
            title = "Sam",
            text = "Are we still on for lunch tomorrow?",
            messages =
                listOf(
                    LauncherNotificationMessage("Sam", "Morning! Got a minute?", StreamFixtures.NOW - 20 * MINUTE),
                    LauncherNotificationMessage(
                        "Sam",
                        "Are we still on for lunch tomorrow?",
                        StreamFixtures.NOW - 4 * MINUTE,
                    ),
                ),
            postedAtEpochMillis = StreamFixtures.NOW - 4 * MINUTE,
        )

    private fun musicNotification() =
        LauncherNotification(
            key = LauncherNotificationKey("0|music|1"),
            packageName = AppPackageName(musicPackage),
            isMediaSession = true,
            title = "Midnight City",
            text = "M83",
            postedAtEpochMillis = StreamFixtures.NOW - MINUTE,
        )

    private val session =
        MediaSessionInfo(
            packageName = musicPackage,
            title = "Midnight City",
            artist = "M83",
            isPlaying = true,
            positionMillis = 83_000L,
            durationMillis = 244_000L,
            controls = setOf(MediaControl.PLAY, MediaControl.PAUSE, MediaControl.PREVIOUS, MediaControl.NEXT),
            queue = listOf(MediaSessionQueueEntry("Reunion", "M83"), MediaSessionQueueEntry("Wait", "M83")),
        )

    private fun host(landOn: String): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications = listOf(chatNotification(), musicNotification()),
            installedApps = ScreenshotFixtures.installedApps,
            canReply = { true },
            canSnooze = { true },
            mediaSessions = listOf(session),
            actions = gateway,
        ).also { it.pages.remember(HomeLayoutDeviceClass.PHONE, landOn) }

    private fun show(
        landOn: String,
        reducedMotion: Boolean = true,
    ) {
        val stream = host(landOn)
        val base = ScreenshotFixtures.homeState().copy(destination = ShellDestination.STREAM)
        val state =
            base.copy(
                launcherSettings =
                    base.launcherSettings.copy(
                        motion =
                            base.launcherSettings.motion.copy(
                                reducedMotionPreference =
                                    if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                            ),
                    ),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    onAction = { action -> sent += action },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitPerformed(count: Int) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.size >= count }
    }

    private fun openTheChat() {
        show(chatPage)
        waitForText("Sam")
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
    }

    private fun theCardIsOpen() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG))
            .fetchSemanticsNodes().isNotEmpty()

    private fun theRowIsThere() =
        composeRule.onAllNodes(hasTestTag(DOCK_CARD_ACTIONS_TEST_TAG))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun tappingTheFrontCardOpensItAndTheDockShowsThatCardsActionsAboveItsIcons() {
        show(chatPage)
        waitForText("Sam")
        assertTrue("no row before a card is open", !theRowIsThere())

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        assertTrue("the card opened", theCardIsOpen())
        assertTrue("the Dock has its actions row", theRowIsThere())
        composeRule.onNodeWithTag(DOCK_CARD_ACTIONS_TEST_TAG).assertIsDisplayed()
        // The reply field, send, Dismiss and Snooze are in the row; the card itself holds none of them.
        composeRule.onAllNodesWithText("Write a reply").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Send reply").assertExists()
        composeRule.onNodeWithContentDescription("Dismiss").assertExists()
        composeRule.onNodeWithContentDescription("Snooze").assertExists()
        // The row is above the Dock's icons.
        val row = composeRule.onNodeWithTag(DOCK_CARD_ACTIONS_TEST_TAG).fetchSemanticsNode()
        val dock = composeRule.onNodeWithTag(com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        assertTrue(row.positionInRoot.y + row.size.height <= dock.positionInRoot.y + dock.size.height)
        assertEquals(
            "the row is part of the Dock, above its icons",
            true,
            row.positionInRoot.y < dock.positionInRoot.y + dock.size.height - ICON_ROW_PX,
        )
    }

    @Test
    fun sendingAReplyThroughTheDocksFieldCallsTheReplyAction() {
        openTheChat()

        composeRule.onNodeWithText("Write a reply").performTextInput("On my way")
        composeRule.onNodeWithContentDescription("Send reply").performClick()
        awaitPerformed(1)

        val reply = performed.single { it.second is StreamAction.Reply }
        assertEquals(StreamAction.Reply("On my way"), reply.second)
        assertEquals("notifications:personal:0|chat|1", reply.first.id.value.removePrefix("com.riffle.sample.chat/"))
    }

    @Test
    fun dismissAndSnoozeInTheDockActOnTheOpenCard() {
        openTheChat()

        composeRule.onNodeWithContentDescription("Snooze").performClick()
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        awaitPerformed(2)

        assertEquals(listOf(StreamAction.Snooze(), StreamAction.Dismiss), performed.map { it.second })
    }

    @Test
    fun aMediaTransportButtonInTheDockCallsTheMediaAction() {
        show(nowPage)
        waitForText("Midnight City")
        composeRule.onNodeWithText("Midnight City").performClick()
        composeRule.waitForIdle()

        assertTrue(theRowIsThere())
        // With the actions in the Dock the open player has no transport of its own: there is one Pause on screen.
        composeRule.onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        awaitPerformed(2)

        assertEquals(listOf(StreamAction.Pause, StreamAction.Next), performed.map { it.second })
        // The queue is on the open player; the row's queue control hides it.
        composeRule.onNodeWithText("Reunion").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hide queue").performClick()
        composeRule.onAllNodesWithText("Reunion").assertCountEquals(0)
    }

    @Test
    fun pressingTheDockWithACardOpenClosesTheCardAndKeepsTheStream() {
        openTheChat()
        assertTrue(theCardIsOpen())

        composeRule.pressDock(distancePx = 150f)

        assertTrue("the card closed", !theCardIsOpen())
        assertTrue("and the row went", !theRowIsThere())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        assertTrue("the press did not close the Stream: $sent", sent.isEmpty())

        // The next press, with nothing open, is the Stream's: it goes back to Home.
        composeRule.pressDock(distancePx = 150f)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun aPressThatStartsOnTheRowsEmptySpaceIsStillTheDocksPress() {
        openTheChat()

        // The row's padding is empty space inside the Dock: a thumb that lands there and drifts sideways still presses.
        composeRule.pressDock(
            distancePx = 150f,
            start = { Offset(3f, center.y) },
            node = composeRule.onNodeWithTag(DOCK_CARD_ACTIONS_TEST_TAG),
        )

        assertTrue("the card closed", !theCardIsOpen())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aTapOnAControlInTheRowIsTheControlsAndNotAPress() {
        openTheChat()

        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        awaitPerformed(1)

        assertEquals(listOf(StreamAction.Dismiss), performed.map { it.second })
        assertTrue("the card stays open: Dismiss is the gateway's to answer", theCardIsOpen())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun backClosesTheCardFirstThenTheStream() {
        openTheChat()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("the card closed", !theCardIsOpen())
        assertTrue("Back did not leave the Stream yet: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun tappingTheOpenCardAgainClosesIt() {
        openTheChat()

        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertTrue(!theCardIsOpen())
        assertTrue(!theRowIsThere())
    }

    @Test
    fun theCardGrowsWithoutReducedMotionToo() {
        show(chatPage, reducedMotion = false)
        waitForText("Sam")
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        assertTrue(theCardIsOpen())
        composeRule.onNodeWithText("Sam: Morning! Got a minute?").assertIsDisplayed()
    }

    // Screenshots of the Dock with its actions row (the goldens are recorded with the others) ---------------------

    @Test
    fun dockWithAConversationsActionsRow() {
        openTheChat()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun dockWithAConversationsActionsRowDarkTheme() {
        openTheChat()
        composeRule.captureScreen()
    }

    @Test
    fun dockWithThePlayersActionsRow() {
        show(nowPage)
        waitForText("Midnight City")
        composeRule.onNodeWithText("Midnight City").performClick()
        composeRule.waitForIdle()
        composeRule.captureScreen()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
        const val ICON_ROW_PX = 40
    }
}

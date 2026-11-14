package com.riffle.app.launcher.stream

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * An open Stream card and its actions in the Dock, on a device (dock modes slice 5; ADR 0003, Amendment 1): through the
 * real shell, Dock and Stream with only the platform services faked, tapping the front card opens it and the Dock gains
 * that card's actions; a reply sent through the Dock's field and a media transport button in it reach the action
 * gateway (the test's fake); pressing the Dock with a card open closes the card and keeps the Stream; Back closes the
 * card first and the Stream next. Motion is reduced so no outcome depends on an animation's timing. The JVM twin is
 * `StreamOpenCardDockTest`.
 */
@RunWith(AndroidJUnit4::class)
class StreamOpenCardInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "open-card").apply { isDaemon = true } }

    // Actions run off the main thread, as in the app, so the list is written from another thread.
    private val performed = CopyOnWriteArrayList<Pair<StreamContent, StreamAction>>()
    private val sent = CopyOnWriteArrayList<LauncherShellAction>()
    private var shellShown by mutableStateOf(true)

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chat = installed("chat")
    private val music = installed("music")
    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

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
            packageName = chat.identity.packageName,
            canDismiss = true,
            title = "Sam",
            text = "Are we still on for lunch tomorrow?",
            messages =
                listOf(
                    LauncherNotificationMessage("Sam", "Morning! Got a minute?", NOW - TWENTY_MINUTES),
                    LauncherNotificationMessage("Sam", "Are we still on for lunch tomorrow?", NOW - FOUR_MINUTES),
                ),
            postedAtEpochMillis = NOW - FOUR_MINUTES,
        )

    private fun musicNotification() =
        LauncherNotification(
            key = LauncherNotificationKey("0|music|1"),
            packageName = music.identity.packageName,
            isMediaSession = true,
            title = "Midnight City",
            text = "M83",
            postedAtEpochMillis = NOW - ONE_MINUTE,
        )

    private val session =
        MediaSessionInfo(
            packageName = music.identity.packageName.value,
            title = "Midnight City",
            artist = "M83",
            isPlaying = true,
            positionMillis = 83_000L,
            durationMillis = 244_000L,
            controls = setOf(MediaControl.PLAY, MediaControl.PAUSE, MediaControl.PREVIOUS, MediaControl.NEXT),
            queue = listOf(MediaSessionQueueEntry("Reunion", "M83")),
        )

    private fun show(landOn: String) {
        val host =
            openCardStreamHost(
                executor = executor,
                notifications = listOf(chatNotification(), musicNotification()),
                sessions = listOf(session),
                installedApps = listOf(chat, music) + docked.map { InstalledApp(it.appIdentity, it.label) },
                actions = gateway,
                nowEpochMillis = NOW,
            )
        host.pages.remember(HomeLayoutDeviceClass.PHONE, landOn)
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(capacity = DOCKED_COUNT, items = docked),
                )
            }
        val base = LauncherShellState(homeLayout = layout, destination = ShellDestination.STREAM)
        val state =
            base.copy(
                installedApps = listOf(chat, music) + docked.map { InstalledApp(it.appIdentity, it.label) },
                launcherSettings =
                    base.launcherSettings.copy(
                        motion =
                            base.launcherSettings.motion.copy(
                                reducedMotionPreference = ReducedMotionPreference.ON,
                            ),
                    ),
            )
        composeRule.setContent {
            RiffleLauncherTheme {
                LauncherShellContent(
                    state =
                        state.copy(
                            destination = if (shellShown) ShellDestination.STREAM else ShellDestination.HOME,
                        ),
                    appIconLoader = EmptyAppIconLoader,
                    stream = host,
                    onAction = { action ->
                        sent += action
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitPerformed(count: Int) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.size >= count }
    }

    private fun openTheChat() {
        show(CHAT_PAGE)
        awaitText("Sam")
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
    }

    private fun cardIsOpen(): Boolean =
        composeRule.onAllNodesWithTag(
            STREAM_OPEN_CARD_TEST_TAG,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun rowIsThere(): Boolean =
        composeRule.onAllNodes(hasTestTag(DOCK_CARD_ACTIONS_TEST_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** A press of the Dock: from its middle toward its edge by a fraction of the window, in steps, then lifted. */
    private fun pressDock() {
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, rootHeight * PRESS_FRACTION / STEPS), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    @Test
    fun tappingTheFrontCardOpensItAndTheDockShowsThatCardsActions() {
        show(CHAT_PAGE)
        awaitText("Sam")
        assertTrue("no row before a card is open", !rowIsThere())

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        assertTrue("the card opened", cardIsOpen())
        composeRule.onNodeWithTag(DOCK_CARD_ACTIONS_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("Write a reply").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Send reply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Snooze").assertIsDisplayed()
    }

    @Test
    fun sendingAReplyThroughTheDocksFieldCallsTheReplyAction() {
        openTheChat()

        composeRule.onNodeWithText("Write a reply").performTextInput("On my way")
        composeRule.onNodeWithContentDescription("Send reply").performClick()
        awaitPerformed(1)

        val reply = performed.single { it.second is StreamAction.Reply }
        assertEquals(StreamAction.Reply("On my way"), reply.second)
    }

    @Test
    fun aMediaTransportButtonInTheDockCallsTheMediaAction() {
        show(NOW_PAGE)
        awaitText("Midnight City")
        composeRule.onNodeWithText("Midnight City").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Pause").performClick()
        awaitPerformed(1)

        assertEquals(StreamAction.Pause, performed.first().second)
    }

    @Test
    fun pressingTheDockWithACardOpenClosesTheCardAndKeepsTheStream() {
        openTheChat()
        assertTrue(cardIsOpen())

        pressDock()

        assertTrue("the card closed", !cardIsOpen())
        assertTrue("and the row went", !rowIsThere())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        assertTrue("the press did not close the Stream: $sent", sent.isEmpty())

        pressDock()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent.toList())
    }

    @Test
    fun backClosesTheCardFirstThenTheStream() {
        openTheChat()
        assertTrue(cardIsOpen())

        composeRule.activityRule.scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertTrue("the card closed", !cardIsOpen())
        assertTrue("Back did not leave the Stream yet: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent.toList())
    }

    private fun installed(name: String) =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.test.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name.replaceFirstChar { it.uppercase() },
        )

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private companion object {
        const val NOW = 1_767_225_600_000L
        const val ONE_MINUTE = 60_000L
        const val FOUR_MINUTES = 4 * ONE_MINUTE
        const val TWENTY_MINUTES = 20 * ONE_MINUTE
        const val DOCKED_COUNT = 4
        const val WAIT_MILLIS = 10_000L
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val PRESS_FRACTION = 0.2f
        val CHAT_PAGE = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "com.riffle.test.chat:personal")
        val NOW_PAGE = StreamPagePlanner.keyOf(StreamSection.NOW)
    }
}

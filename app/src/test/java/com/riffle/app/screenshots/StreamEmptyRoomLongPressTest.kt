package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.STREAM_COLUMN_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
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
 * The Stream's empty room (dock modes plan, "Long-press coverage"): a tap closes the Stream, as it always did, and a
 * long-press opens the options of the page in front (Show as, Move, Hide: the ones the long-press of its Dock tile
 * offers) where the finger is, closing nothing. Folded the room is the whole window; unfolded it is the column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamEmptyRoomLongPressTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "stream-room").apply { isDaemon = true } }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(state())

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val dp get() = composeRule.density.density

    private fun host(deviceClass: HomeLayoutDeviceClass): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("0|chat|1"),
                        packageName = AppPackageName(chatPackage),
                        canDismiss = true,
                        title = "Sam",
                        text = "Are we still on for lunch tomorrow?",
                        messages =
                            listOf(
                                LauncherNotificationMessage(
                                    "Sam",
                                    "Are we still on for lunch tomorrow?",
                                    StreamFixtures.NOW - FOUR_MINUTES,
                                ),
                            ),
                        postedAtEpochMillis = StreamFixtures.NOW - FOUR_MINUTES,
                    ),
                ),
            installedApps = ScreenshotFixtures.installedApps,
        ).also { it.pages.remember(deviceClass, chatPage) }

    private fun state(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        destination: ShellDestination = ShellDestination.HOME,
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState(deviceClass).withHomePages(2).withManyApps().copy(destination = destination)
        val settings = base.launcherSettings
        return base.copy(
            launcherSettings =
                settings.copy(
                    motion = settings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
                ),
        )
    }

    private fun show(deviceClass: HomeLayoutDeviceClass) {
        shell = state(deviceClass, ShellDestination.STREAM)
        val stream = host(deviceClass)
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    onAction = { action ->
                        sent += action
                        shell =
                            when (action) {
                                LauncherShellAction.OpenHome -> shell.copy(destination = ShellDestination.HOME)
                                else -> shell.answering(action)
                            }
                    },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Sam").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun foldedALongPressOnTheEmptyRoomOpensThePageOptionsAndCloseNothing() {
        show(HomeLayoutDeviceClass.PHONE)
        composeRule.onRoot().performTouchInput { longClick(Offset(width / 2f, 120f * dp)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hide section").assertIsDisplayed()
        composeRule.onAllNodesWithText("Show as", substring = true).assertCountEquals(1)
        assertTrue("a long-press did not close the Stream: $sent", sent.isEmpty())
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
    }

    @Test
    fun foldedATapOnTheEmptyRoomStillClosesTheStream() {
        show(HomeLayoutDeviceClass.PHONE)
        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, 120f * dp)) }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun foldedALongPressOnTheRoomUnderTheStatusBarIsTheSameMenu() {
        show(HomeLayoutDeviceClass.PHONE)
        composeRule.setSystemBarInsets(topDp = 24, bottomDp = 48)
        composeRule.onRoot().performTouchInput { longClick(Offset(width / 2f, 6f * dp)) }
        composeRule.onNodeWithText("Hide section").assertIsDisplayed()
    }

    @Test
    fun foldedALongPressOnACardIsTheCardsAndNotThePageOptions() {
        show(HomeLayoutDeviceClass.PHONE)
        composeRule.onNodeWithText("Sam").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Hide section").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun unfoldedALongPressOnTheColumnsEmptyRoomOpensThePageOptions() {
        show(HomeLayoutDeviceClass.FOLDABLE)
        val column = composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { longClick(Offset(column.center.x, column.top + 20f * dp)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hide section").assertIsDisplayed()
        assertTrue("it did not close the Stream: $sent", sent.isEmpty())
    }

    private companion object {
        const val FOUR_MINUTES = 4 * 60_000L
    }
}

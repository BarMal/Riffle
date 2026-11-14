package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the long-press coverage tests share (dock modes plan, "Long-press coverage"): every surface is long-pressed on a
 * grid of points across the whole window, every 48 dp (and 2 dp inside each edge), skipping the points that land on an
 * item, the Dock's icons or the handle, and the points that open the surface's menu are counted. A dead point is one
 * where nothing opened. The system bars are simulated (a 24 dp status bar, a 48 dp navigation bar) so the room under
 * them is probed too. A test fails with the map of the dead points: `#` opened a menu, `.` is dead, `o` was left out.
 *
 * The shell is a stand-in that records every action and answers none: a long-press must send nothing (a release that
 * counts as a tap would close the Library or the Stream).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
abstract class LongPressCoverageBase {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected val haptics = CountingHaptics()

    /** Every action the surface sent: a long-press must not send one (a release that counts as a tap would). */
    protected val actions = mutableListOf<LauncherShellAction>()
    protected var shell by mutableStateOf(ScreenshotFixtures.homeState())

    protected fun folded(dock: DockPosition? = null) =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, dock).withHomePages(3).withManyApps()

    protected fun unfolded(dock: DockPosition? = null) =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, dock).withHomePages(3).withManyApps()

    protected fun show(
        state: LauncherShellState,
        stream: StreamHost = emptyStreamHost(),
        streamShown: Boolean = false,
    ) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    streamShown = streamShown,
                    haptics = haptics,
                    adaptiveStageWindowLayout =
                        screenshotWindowLayout(
                            if (shell.homeLayoutSet.activeKey.deviceClass == HomeLayoutDeviceClass.PHONE) {
                                AdaptiveStagePosture.COMPACT
                            } else {
                                AdaptiveStagePosture.UNFOLDED
                            },
                        ),
                    onAction = { action: LauncherShellAction -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.setSystemBarInsets(topDp = STATUS_BAR_DP, bottomDp = NAV_BAR_DP)
    }

    protected fun probe(): ProbeResult {
        val skip =
            composeRule.itemRects(listOf(HOME_HANDLE_TEST_TAG))
        return composeRule.probeLongPress(haptics, skip)
    }

    protected fun assertNoDeadPoint(
        name: String,
        result: ProbeResult,
    ) {
        println("LONG-PRESS MAP $name\n${result.deadRegions()}")
        assertTrue(
            "$name: ${result.deadRegions()}\n${result.callerSummary()}\nactions sent: ${actions.size}",
            result.dead.isEmpty(),
        )
        assertTrue("$name: a long-press sent $actions", actions.isEmpty())
    }

    companion object {
        const val STATUS_BAR_DP = 24
        const val NAV_BAR_DP = 48
    }
}

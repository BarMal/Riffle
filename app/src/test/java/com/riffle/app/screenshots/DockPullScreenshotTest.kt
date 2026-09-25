package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock pull from Cards (Home) to Library (#1206, #1207) through the real [HomeDestination]: at
 * rest, mid-pull (the dock and Cards lifted with the finger, the dock background faded, Library
 * following in from below) and settled on Library once the switch lands. The reduced-motion
 * variant shows the mid-pull crossfade instead of the slide.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPullScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardsToLibraryStart() {
        render(ScreenshotFixtures.cardsState())
        composeRule.captureScreen()
    }

    @Test
    fun cardsToLibraryMid() {
        render(ScreenshotFixtures.cardsState())
        pullUp(fractionOfHeight = 0.35f, release = false)
        composeRule.captureScreen()
    }

    @Test
    fun cardsToLibraryEnd() {
        render(ScreenshotFixtures.cardsState())
        pullUp(fractionOfHeight = 0.6f, release = true)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.captureScreen()
    }

    @Test
    fun cardsToLibraryMidReducedMotion() {
        val reduced =
            LauncherSettings(motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON))
        render(ScreenshotFixtures.cardsState(launcherSettings = reduced))
        pullUp(fractionOfHeight = 0.35f, release = false)
        composeRule.captureScreen()
    }

    /**
     * The dock's own re-orientation (dock-reorient decisions, follow-up to #1278), for a dock that
     * actually changes edge on the switch: Home on the right (Decision: right dock pulls left),
     * Library on its default bottom edge. Captured through the COMMIT settle at roughly its start,
     * ~40%, ~80% and its end, so a regression in the frost/tilt/reveal-hold ramp shows up as a
     * screenshot diff at the point it appears rather than only once everything has finished.
     */
    @Test
    fun rightToBottomReorientStart() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.RIGHT))
        composeRule.captureScreen()
    }

    @Test
    fun rightToBottomReorientEarlyInTheSettle() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.RIGHT))
        pullLeft(fractionOfWidth = 0.6f, release = true)
        composeRule.mainClock.advanceTimeBy((SETTLE_MILLIS * REORIENT_EARLY_FRACTION).toLong())
        composeRule.captureScreen()
    }

    @Test
    fun rightToBottomReorientLateInTheSettle() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.RIGHT))
        pullLeft(fractionOfWidth = 0.6f, release = true)
        composeRule.mainClock.advanceTimeBy((SETTLE_MILLIS * REORIENT_LATE_FRACTION).toLong())
        composeRule.captureScreen()
    }

    @Test
    fun rightToBottomReorientEnd() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.RIGHT))
        pullLeft(fractionOfWidth = 0.6f, release = true)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.captureScreen()
    }

    private fun render(initial: LauncherShellState) {
        val iconLoader = SolidColorAppIconLoader()
        var state by mutableStateOf(initial)
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = iconLoader,
                    adaptiveStageWindowLayout = screenshotWindowLayout(AdaptiveStagePosture.UNKNOWN),
                    onAction = { action ->
                        // Stands in for the shell: the pull's switch lands on Library.
                        val switch = action as? LauncherShellAction.SelectLauncherViewMode
                        if (switch?.mode == LauncherViewMode.HOME_SCREEN_LIBRARY) {
                            state = ScreenshotFixtures.libraryState()
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** A steady upward pull from the middle of the dock, by a fraction of the window height. */
    private fun pullUp(
        fractionOfHeight: Float,
        release: Boolean,
    ) {
        val total = composeRule.onRoot().fetchSemanticsNode().size.height * fractionOfHeight
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(PULL_STEPS) { moveBy(Offset(0f, -total / PULL_STEPS)) }
            if (release) up()
        }
        composeRule.waitForIdle()
    }

    /**
     * A steady leftward pull from the middle of the dock, by a fraction of the window width -- a
     * right-edge dock's natural pull direction.
     */
    private fun pullLeft(
        fractionOfWidth: Float,
        release: Boolean,
    ) {
        val total = composeRule.onRoot().fetchSemanticsNode().size.width * fractionOfWidth
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(PULL_STEPS) { moveBy(Offset(-total / PULL_STEPS, 0f)) }
            if (release) up()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val PULL_STEPS = 12
        const val SETTLE_MILLIS = 2_000L
        const val REORIENT_EARLY_FRACTION = 0.4f
        const val REORIENT_LATE_FRACTION = 0.8f
    }
}

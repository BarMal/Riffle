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

    private companion object {
        const val PULL_STEPS = 12
        const val SETTLE_MILLIS = 2_000L
    }
}

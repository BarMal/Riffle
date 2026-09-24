package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.AdaptiveStageAppearanceTuningOverlay
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Cards appearance tuning overlay: the editor sheet expanded over the preview surface it
 * shapes, as it first opens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class AppearanceTuningScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overlayCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun overlayCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun overlayCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun overlayUnfolded() {
        render()
    }

    private fun render() {
        val state = ScreenshotFixtures.cardsState().settingsSurfaceState()
        composeRule.setContent {
            ScreenshotBackdrop {
                AdaptiveStageAppearanceTuningOverlay(
                    state = state,
                    onAction = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}

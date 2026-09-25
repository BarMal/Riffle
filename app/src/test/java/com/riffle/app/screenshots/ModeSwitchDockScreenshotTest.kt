package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two sides of a Library <-> Cards switch through the real [HomeDestination]. The dock is drawn
 * once, outside the mode surface (#1205), so it sits in the same place at the same size in both;
 * only what lies beside it, and what the mode derives on its dynamic side (Cards marks the selected
 * stage), may differ between the two images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ModeSwitchDockScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryDockCompact() {
        render(ScreenshotFixtures.libraryState())
    }

    @Test
    fun cardsDockCompact() {
        render(ScreenshotFixtures.cardsState())
    }

    private fun render(state: LauncherShellState) {
        val iconLoader = SolidColorAppIconLoader()
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = iconLoader,
                    adaptiveStageWindowLayout = screenshotWindowLayout(AdaptiveStagePosture.UNKNOWN),
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}

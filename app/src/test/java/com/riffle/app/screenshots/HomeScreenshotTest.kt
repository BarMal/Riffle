package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The standard grid Home through the real [HomeDestination]: a page of apps with the dock on the
 * bottom edge and on a side edge, across the posture matrix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gridBottomDockCompact() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.BOTTOM))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun gridBottomDockCompactDark() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.BOTTOM))
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun gridBottomDockCompactLargeFont() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.BOTTOM))
    }

    @Test
    fun gridSideDockCompact() {
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.LEFT))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun gridTemplateDockUnfolded() {
        // No configured edge: the foldable template's own default dock position.
        render(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun gridTemplateDockTabletLandscape() {
        render(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.TABLET))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun gridBottomDockTabletLandscape() {
        render(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.TABLET, DockPosition.BOTTOM))
    }

    private fun render(state: LauncherShellState) {
        val iconLoader = SolidColorAppIconLoader()
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = iconLoader,
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}

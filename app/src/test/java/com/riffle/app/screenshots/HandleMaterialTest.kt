package com.riffle.app.screenshots

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.drawToBitmap
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The page handle is drawn as the same material as the Dock it stands in front of (its "Dock background" fill and
 * colour, its edge lights, a pill whose corners follow the Dock's), through the real Home: the pill's fill is read back
 * as pixels, against the wallpaper just outside it, and follows the Dock's own setting (clear at 0%, solid at 100%,
 * the Thick fill under reduced transparency). The screenshots show the handle beside the Dock, bottom and side, light
 * and dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HandleMaterialTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun LauncherShellState.withDockBackground(percent: Int): LauncherShellState {
        val layout = homeLayout.copy(dock = homeLayout.dock.copy(backgroundAlphaPercent = percent))
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    private fun show(
        state: LauncherShellState,
        reducedTransparency: Boolean = false,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** How far the pill's own fill (inside it, clear of its glyph and dots) is from the wallpaper just outside it. */
    private fun fillAgainstWallpaper(): Float {
        val density = composeRule.density.density
        val handle = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val shot = composeRule.activity.window.decorView.drawToBitmap()
        // The right-hand end of the pill (past the dots), at its middle, and the same x in the target's clear margin.
        val x = (handle.right - 9f * density).toInt()
        val inside = shot.getPixel(x, handle.center.y.toInt())
        val outside = shot.getPixel(x, (handle.top + 2f * density).toInt())
        return distance(inside, outside)
    }

    private fun distance(
        a: Int,
        b: Int,
    ): Float =
        (abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))) /
            MAX_DISTANCE

    @Test
    fun theHandleFollowsTheDocksBackgroundSettingFromClearToSolid() {
        show(ScreenshotFixtures.homeState().withHomePages(3).withDockBackground(0))
        val clear = fillAgainstWallpaper()
        assertTrue("a clear Dock gives a clear handle: $clear", clear < CLEAR_MAX)
    }

    @Test
    fun withTheDefaultBackgroundTheHandleHasTheDocksFill() {
        show(ScreenshotFixtures.homeState().withHomePages(3))
        val filled = fillAgainstWallpaper()
        assertTrue("the handle is filled like the Dock: $filled", filled > FILLED_MIN)
    }

    @Test
    fun aSolidDockGivesASolidHandle() {
        show(ScreenshotFixtures.homeState().withHomePages(3).withDockBackground(100))
        val solid = fillAgainstWallpaper()
        assertTrue("a solid Dock gives a solid handle: $solid", solid > SOLID_MIN)
    }

    @Test
    fun theHandleBesideABottomDockLight() = shoot(ScreenshotFixtures.homeState().withHomePages(3))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theHandleBesideABottomDockDark() = shoot(ScreenshotFixtures.homeState().withHomePages(3))

    @Test
    fun theHandleBesideASideDockLight() =
        shoot(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, DockPosition.RIGHT).withHomePages(3))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theHandleBesideASideDockDark() =
        shoot(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, DockPosition.LEFT).withHomePages(3))

    @Test
    fun theHandleBesideAClearDockLight() = shoot(ScreenshotFixtures.homeState().withHomePages(3).withDockBackground(0))

    @Test
    fun theHandleUnderReducedTransparencyLight() {
        show(ScreenshotFixtures.homeState().withHomePages(3), reducedTransparency = true)
        composeRule.captureScreen()
    }

    private fun shoot(state: LauncherShellState) {
        show(state)
        composeRule.captureScreen()
    }

    private companion object {
        const val MAX_DISTANCE = 765f
        const val CLEAR_MAX = 0.04f
        const val FILLED_MIN = 0.05f
        const val SOLID_MIN = 0.1f
    }
}

package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.ResolvedLiquidGlass
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Glass over the system wallpaper. On a real phone the wallpaper is outside the app, so all the glass
 * can do is lay its flat tint over it. These tests mimic that with a pure red root (the wallpaper) and
 * check the centre of a surface -- well away from the 1dp edge lights and the 24dp the border takes to
 * fade -- is exactly the tint at the weight's fill alpha over red: `tint * a + red * (1 - a)`.
 *
 * Assumptions: the test device renders sRGB with plain source-over compositing (so 8-bit rounding is
 * the only error, hence a 0.03 tolerance, a few 1/255 steps), and the surface is 200dp square so the
 * centre is 100dp from every edge.
 */
@RunWith(AndroidJUnit4::class)
class GlassOverWallpaperTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val wallpaper = Color.Red
    private val glassSettings = ResolvedLiquidGlass(reducedMotion = false)

    @Test
    fun regularGlassShowsTheWallpaperThroughItsFlatTint() {
        val tint = Color(0xFFFFFBFE)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(wallpaper)) {
                LightTheme {
                    GlassSurface(
                        modifier = Modifier.size(200.dp).testTag(TAG),
                        weight = GlassWeight.Regular,
                        tint = tint,
                    )
                }
            }
        }

        val expected = glassFillColor(GlassWeight.Regular, tint).compositeOver(wallpaper)
        val actual = centrePixel()

        assertClose(expected, actual)
        // Red is still showing: the green and blue channels are the tint's share only, far below white.
        assertTrue("wallpaper hidden: $actual", actual.red > 0.9f && actual.green < 0.8f)
    }

    @Test
    fun dockBackgroundAtZeroPercentIsClearOverTheWallpaper() {
        setDock(backgroundAlphaPercent = 0)
        assertClose(wallpaper, centrePixel())
    }

    @Test
    fun dockBackgroundAtOneHundredPercentIsTheTintOverTheWallpaper() {
        setDock(backgroundAlphaPercent = 100)
        assertClose(lightScheme.surfaceVariant, centrePixel())
    }

    @Test
    fun dockBackgroundAtFiftyPercentIsHalfTheTintOverTheWallpaper() {
        setDock(backgroundAlphaPercent = 50)
        assertClose(dockFillColor(50, lightScheme.surfaceVariant).compositeOver(wallpaper), centrePixel())
    }

    private fun setDock(backgroundAlphaPercent: Int) {
        val dock = DockModel(capacity = 4, backgroundAlphaPercent = backgroundAlphaPercent)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(wallpaper)) {
                LightTheme {
                    Box(
                        Modifier
                            .size(240.dp, 96.dp)
                            .testTag(TAG)
                            .dockSurfaceAppearance(dock),
                    )
                }
            }
        }
    }

    @Composable
    private fun LightTheme(content: @Composable () -> Unit) {
        RiffleLauncherTheme(
            themeMode = LauncherThemeMode.LIGHT,
            dynamicColor = false,
            liquidGlass = glassSettings,
            content = content,
        )
    }

    private fun centrePixel(): Color {
        val image = composeRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        return image[image.width / 2, image.height / 2]
    }

    private fun assertClose(
        expected: Color,
        actual: Color,
    ) {
        val ok =
            kotlin.math.abs(expected.red - actual.red) < TOLERANCE &&
                kotlin.math.abs(expected.green - actual.green) < TOLERANCE &&
                kotlin.math.abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("expected=$expected actual=$actual", ok)
    }

    private companion object {
        const val TAG = "glassOverWallpaper"
        const val TOLERANCE = 0.03f
    }
}

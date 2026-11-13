package com.riffle.app.launcher

import android.os.Build
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
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.ResolvedLiquidGlass
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Glass over the system wallpaper. On a real phone the wallpaper is outside the app's content, so the
 * backdrop the glass shader samples is TRANSPARENT there. These tests mimic that: a pure red root
 * (the wallpaper), and beside the glass a captured backdrop whose content is transparent. The glass
 * must show the red through the tint; a shader that always outputs an opaque pixel shows an opaque
 * dark tint with no red in it (the regression these guard against).
 *
 * They run the real AGSL shader path (API 33+, a backdrop installed, glass settings provided, not
 * Robolectric), so they assume API 33+ and are meaningless on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class GlassOverWallpaperTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val wallpaper = Color.Red
    private val glassSettings = ResolvedLiquidGlass(0.45f, 0.2f, false)

    @Test
    fun regularGlassOverATransparentBackdropShowsTheWallpaperThroughTheTint() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val tint = Color(0xFFFFFBFE)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(wallpaper)) {
                LightTheme {
                    ProvideLiquidGlassBackdrop { backdropCapture ->
                        // The captured content is empty: the wallpaper area. It is a sibling of the glass, so the
                        // glass never samples itself.
                        Box(Modifier.fillMaxSize().then(backdropCapture))
                        GlassSurface(
                            modifier = Modifier.size(200.dp).testTag(TAG),
                            weight = GlassWeight.Regular,
                            tint = tint,
                        )
                    }
                }
            }
        }

        val tokens = GlassMaterialTokens.regular
        val alpha = tokens.shaderTintAlphaOverWallpaper(tokens.effectiveFrost(glassSettings.frostStrengthFraction))
        val expected = tint.copy(alpha = alpha).compositeOver(wallpaper)
        val actual = centrePixel()

        assertClose(expected, actual)
        // Red is still showing: the green and blue channels are the tint's share only, far below white.
        assertTrue("wallpaper hidden: $actual", actual.red > 0.9f && actual.green < 0.8f)
    }

    @Test
    fun dockBackgroundAtZeroPercentIsClearOverTheWallpaper() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        setDock(backgroundAlphaPercent = 0)
        assertClose(wallpaper, centrePixel())
    }

    @Test
    fun dockBackgroundAtOneHundredPercentIsTheTintOverTheWallpaper() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        setDock(backgroundAlphaPercent = 100)
        assertClose(lightScheme.surfaceVariant, centrePixel())
    }

    @Test
    fun dockBackgroundAtFiftyPercentIsHalfTheTintOverTheWallpaper() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        setDock(backgroundAlphaPercent = 50)
        assertClose(lightScheme.surfaceVariant.copy(alpha = 0.5f).compositeOver(wallpaper), centrePixel())
    }

    private fun setDock(backgroundAlphaPercent: Int) {
        val dock = DockModel(capacity = 4, backgroundAlphaPercent = backgroundAlphaPercent)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(wallpaper)) {
                LightTheme {
                    ProvideLiquidGlassBackdrop { backdropCapture ->
                        Box(Modifier.fillMaxSize().then(backdropCapture))
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
        // The sheen adds a little light on top, hence the tolerance.
        val ok =
            kotlin.math.abs(expected.red - actual.red) < TOLERANCE &&
                kotlin.math.abs(expected.green - actual.green) < TOLERANCE &&
                kotlin.math.abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("expected=$expected actual=$actual", ok)
    }

    private companion object {
        const val TAG = "glassOverWallpaper"
        const val TOLERANCE = 0.08f
    }
}

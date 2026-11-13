package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherPanelSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherPanelUsesSharedGlassSurfaceToken() {
        val wallpaper = Color.Red
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(wallpaper)) {
                RiffleLauncherTheme(
                    themeMode = LauncherThemeMode.LIGHT,
                    dynamicColor = false,
                ) {
                    LauncherPanel(
                        title = "Apps",
                        onAction = {},
                        showSettingsAction = false,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {}
                }
            }
        }

        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val actual = image[image.width / 2, image.height / 2]
        // The surface is Thick glass: a flat fill of the panel tint at that weight's fill alpha over whatever is
        // behind it. The centre is well away from the edge lights (a 1dp border and hairline), so the only slack
        // needed is 8-bit rounding and the compositing order of the test device's GPU.
        val glass = GlassMaterialTokens.forWeight(GlassWeight.Thick)
        val expected =
            launcherThemeSurfaceTokens(colorScheme = lightScheme)
                .panelColor
                .copy(alpha = glass.fillAlpha)
                .compositeOver(wallpaper)

        assertColorClose(expected, actual, tolerance = GLASS_TOLERANCE)
    }

    private fun assertColorClose(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.02f,
    ) {
        assertTrue(
            "red channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.red - actual.red) < tolerance,
        )
        assertTrue(
            "green channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.green - actual.green) < tolerance,
        )
        assertTrue(
            "blue channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.blue - actual.blue) < tolerance,
        )
    }

    private companion object {
        const val GLASS_TOLERANCE = 0.03f
    }
}

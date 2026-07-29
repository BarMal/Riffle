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
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
                    themePreset = LauncherThemePreset.GLASS,
                    themeColors = LauncherThemeColors(backgroundArgb = 0xFF0000FF.toInt()),
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
        val expected =
            launcherThemeSurfaceTokens(
                themePreset = LauncherThemePreset.GLASS,
                colorScheme = lightScheme.withThemeColors(LauncherThemeColors(backgroundArgb = 0xFF0000FF.toInt())),
            ).panelColor.compositeOver(wallpaper)

        assertColorClose(expected, actual)
    }

    private fun assertColorClose(
        expected: Color,
        actual: Color,
    ) {
        assertTrue(
            "red channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.red - actual.red) < 0.02f,
        )
        assertTrue(
            "green channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.green - actual.green) < 0.02f,
        )
        assertTrue(
            "blue channel differs: expected=$expected actual=$actual",
            kotlin.math.abs(expected.blue - actual.blue) < 0.02f,
        )
    }
}

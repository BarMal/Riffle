package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun folderSurfaceUsesTheSharedGlassPanelTreatment() {
        val wallpaper = Color.Red
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(wallpaper)) {
                RiffleLauncherTheme(themeMode = LauncherThemeMode.LIGHT, dynamicColor = false) {
                    FolderSurface(
                        folder =
                            FolderItem(
                                id = LauncherItemId("folder"),
                                label = "Folder",
                                items = emptyList(),
                            ),
                        layout = HomeLayoutDefaults.standard(),
                        installedApps = emptyList(),
                        appIconLoader = EmptyAppIconLoader,
                        onDismiss = {},
                        onAction = {},
                    )
                }
            }
        }

        val image = composeRule.onNodeWithTag(FOLDER_SURFACE_ROOT_TEST_TAG).captureToImage().toPixelMap()
        // The shared panel uses a rounded shape, so its corner is intentionally
        // transparent and shows the wallpaper. Sample the interior instead.
        val actual = image[image.width / 2, image.height / 2]
        val corner = image[0, 0]
        // The surface is Regular glass: the weight's flat fill (see glassFillColor) mixed with the panel tint over whatever is
        // behind it. The centre is well away from the edge lights (a 1dp border and hairline), so the only slack
        // needed is 8-bit rounding and the compositing order of the test device's GPU.
        val expected =
            launcherThemeSurfaceTokens(colorScheme = lightScheme)
                .panelColor
                .let { panel -> glassFillColor(GlassWeight.Regular, panel) }
                .compositeOver(wallpaper)

        assertColorClose(expected, actual, tolerance = GLASS_TOLERANCE)
        assertColorClose(wallpaper, corner)
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

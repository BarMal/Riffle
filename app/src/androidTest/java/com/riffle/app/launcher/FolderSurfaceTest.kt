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
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
        val panel = Color.Blue
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(wallpaper)) {
                RiffleLauncherTheme(
                    themePreset = LauncherThemePreset.GLASS,
                    themeColors = LauncherThemeColors(backgroundArgb = 0xFF0000FF.toInt()),
                ) {
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
        val actual = image[image.width - 1, image.height - 1]
        val expected = panel.copy(alpha = 0.78f).compositeOver(wallpaper)

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

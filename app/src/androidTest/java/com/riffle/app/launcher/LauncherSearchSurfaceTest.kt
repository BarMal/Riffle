package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSearchSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchUsesIconResultsByDefaultAndKeepsAppLongPressActions() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    SearchSurface(
                        state =
                            SearchSurfaceState(
                                query = "",
                                filters = AppSearchFilters(),
                                installedApps = listOf(camera),
                                results = listOf(camera),
                                homeLayout = HomeLayoutDefaults.standard(),
                            ),
                        appListContext = searchContext(),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_RESULT_ICON_GRID_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(camera.label).performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to home").assertExists()
    }

    @Test
    fun webSearchPillStaysAboveTheImeInset() {
        val imeBottom = with(composeRule.density) { 200.dp.roundToPx() }
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(400.dp, 800.dp).testTag(SEARCH_WINDOW_TEST_TAG),
                ) {
                    SearchSurface(
                        state =
                            SearchSurfaceState(
                                query = "camera",
                                filters = AppSearchFilters(),
                                installedApps = listOf(camera),
                                results = listOf(camera),
                                homeLayout = HomeLayoutDefaults.standard(),
                            ),
                        appListContext = searchContext(),
                        onAction = {},
                        imeInsets = WindowInsets(0, 0, 0, imeBottom),
                    )
                }
            }
        }

        val windowBounds = composeRule.onNodeWithTag(SEARCH_WINDOW_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val pillBounds = composeRule.onNodeWithTag(SEARCH_WEB_PILL_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(pillBounds.bottom <= windowBounds.bottom - imeBottom)
        composeRule.onNodeWithTag(SEARCH_WEB_PILL_TEST_TAG).assertHasClickAction()
        composeRule.onNodeWithText("Search web").assertExists()
        composeRule.onNodeWithText("Images").assertDoesNotExist()
        composeRule.onNodeWithText("News").assertDoesNotExist()
        composeRule.onNodeWithText("Videos").assertDoesNotExist()
    }

    @Test
    fun searchGlassSurfaceKeepsContrastForMidToneBackgroundOnDarkAndLightWallpapers() {
        val themeColors = LauncherThemeColors(backgroundArgb = 0xFF808080.toInt())
        val wallpaperColor = mutableStateOf(Color.Black)
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(wallpaperColor.value)) {
                RiffleLauncherTheme(
                    themePreset = LauncherThemePreset.GLASS,
                    themeColors = themeColors,
                ) {
                    SearchSurface(
                        state =
                            SearchSurfaceState(
                                query = "",
                                filters = AppSearchFilters(),
                                installedApps = listOf(camera),
                                results = listOf(camera),
                                homeLayout = HomeLayoutDefaults.standard(),
                            ),
                        appListContext = searchContext(),
                        onAction = {},
                    )
                }
            }
        }

        listOf(Color.Black, Color.White).forEach { wallpaper ->
            composeRule.runOnIdle { wallpaperColor.value = wallpaper }
            composeRule.waitForIdle()

            val controls = composeRule.onNodeWithTag(SEARCH_CONTROLS_TEST_TAG).captureToImage().toPixelMap()
            val renderedSurface = controls[controls.width / 2, 4]
            val tokens =
                launcherThemeSurfaceTokens(
                    themePreset = LauncherThemePreset.GLASS,
                    colorScheme = lightScheme.withThemeColors(themeColors),
                )
            assertTrue(
                "search Glass content must contrast over $wallpaper",
                contrastRatio(tokens.panelContentColor, renderedSurface) >= 4.5f,
            )
        }
    }

    private fun searchContext(): AppListContext =
        AppListContext(
            homeLayout = HomeLayoutDefaults.standard(),
            overlayDock = OverlayDockSettings(),
            notificationGroupsByApp = emptyList(),
            appIconLoader = EmptyAppIconLoader,
            onAction = {},
        )

    private companion object {
        const val SEARCH_WINDOW_TEST_TAG = "search-window"

        val camera =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.camera"),
                        activityName = AppActivityName(".CameraActivity"),
                    ),
                label = "Camera",
            )
    }
}

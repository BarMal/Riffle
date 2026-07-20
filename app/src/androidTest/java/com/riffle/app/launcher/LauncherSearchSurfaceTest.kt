package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
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
    fun webExamplesUseDescriptiveSourceIconsInsteadOfTextAbbreviations() {
        composeRule.setContent {
            MaterialTheme {
                SearchWebPanel(
                    preview = checkNotNull(searchWebPreview("camera")),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Images search icon").assertExists()
        composeRule.onNodeWithContentDescription("News search icon").assertExists()
        composeRule.onNodeWithContentDescription("Videos search icon").assertExists()
        composeRule.onNodeWithText("IMG").assertDoesNotExist()
        composeRule.onNodeWithText("NEW").assertDoesNotExist()
        composeRule.onNodeWithText("VID").assertDoesNotExist()
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

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListContextMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appLongPressKeepsPlatformShortcutsInTheirOwnMenu() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppList(
                        apps = listOf(camera),
                        emptyText = "No apps",
                        context =
                            AppListContext(
                                homeLayout = HomeLayoutDefaults.standard(),
                                overlayDock = OverlayDockSettings(),
                                notificationGroupsByApp = emptyList(),
                                appShortcutsByApp = cameraShortcuts,
                                appIconLoader = EmptyAppIconLoader,
                                onAction = {},
                            ),
                    )
                }
            }
        }

        composeRule.onNodeWithText(camera.label).performTouchInput { longClick() }
        composeRule.onNodeWithText("App shortcuts (2)").performClick()
        composeRule.onNodeWithText("Compose message").assertExists()
        composeRule.onNodeWithText("Scan document").assertExists()
    }

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
        val cameraShortcuts =
            mapOf(
                camera.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("compose"),
                            appIdentity = camera.identity,
                            shortLabel = "Compose",
                            longLabel = "Compose message",
                        ),
                        AppShortcut(
                            id = AppShortcutId("scan"),
                            appIdentity = camera.identity,
                            shortLabel = "Scan document",
                        ),
                    ),
            )
    }
}

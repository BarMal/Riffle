package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPageContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionsPageKeepsOptionalAccessInFeatureContexts() {
        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state = LauncherShellState().settingsSurfaceState(),
                    page = SettingsPage.PERMISSIONS,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Default home app").assertExists()
        composeRule.onNodeWithText("Notifications").assertDoesNotExist()
        composeRule.onNodeWithText("Floating dock").assertDoesNotExist()
    }

    @Test
    fun deniedFloatingDockAccessShowsAContextualRetry() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            overlayDockPermissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
                            launcherSettings = LauncherSettings(overlayDock = OverlayDockSettings(enabled = true)),
                        ).settingsSurfaceState(),
                    page = SettingsPage.FLOATING_DOCK,
                    onPageSelected = {},
                    onAction = actions::add,
                )
            }
        }

        composeRule
            .onNodeWithText("Overlay access is not allowed. Allow it to show the Floating dock.")
            .assertExists()
        composeRule.onNodeWithText("Allow overlay access").performClick()

        assertEquals(listOf(LauncherShellAction.RequestOverlayDockPermission), actions)
    }
}

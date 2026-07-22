package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.AppearanceSettings
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
    fun legacyFullscreenShowsCheckedIndependentBarControlsThatDispatchActions() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            launcherSettings =
                                LauncherSettings(
                                    appearance = AppearanceSettings(fullscreenHome = true),
                                ),
                        ).settingsSurfaceState(),
                    page = SettingsPage.APPEARANCE,
                    onPageSelected = {},
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Fullscreen home").assertDoesNotExist()
        composeRule
            .onNode(isToggleable() and hasAnyAncestor(hasText("Hide status bar")))
            .assertIsOn()
            .performSemanticsAction(SemanticsActions.OnClick) { onClick -> onClick() }
        composeRule
            .onNode(isToggleable() and hasAnyAncestor(hasText("Hide navigation bar")))
            .assertIsOn()
            .performSemanticsAction(SemanticsActions.OnClick) { onClick -> onClick() }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    LauncherShellAction.SelectHomeStatusBarHidden(hidden = false),
                    LauncherShellAction.SelectHomeNavigationBarHidden(hidden = false),
                ),
                actions,
            )
        }
    }

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
    fun pendingHomeRoleRequestSuppressesHomeRoleActionInEverySettingsEntryPoint() {
        var currentPage by mutableStateOf(SettingsPage.MAIN)
        val pendingState =
            LauncherShellState(
                firstRunStatus = FirstRunStatus.REQUESTING_HOME_ROLE,
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            ).settingsSurfaceState()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state = pendingState,
                    page = currentPage,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        listOf(SettingsPage.MAIN, SettingsPage.PERMISSIONS).forEach { page ->
            composeRule.runOnIdle { currentPage = page }
            composeRule
                .onNodeWithText("Default home app")
                .assertHasNoClickAction()
            composeRule.onNodeWithText("Checking whether Riffle is your Home app.").assertExists()
            composeRule.onNodeWithText("Checking").assertExists()
        }
    }

    @Test
    fun defaultHomeStatusDoesNotRequestTheHomeRoleAgain() {
        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                        ).settingsSurfaceState(),
                    page = SettingsPage.PERMISSIONS,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Default home app")
            .assertHasNoClickAction()
        composeRule.onNodeWithText("Riffle is default").assertExists()
        composeRule.onNodeWithText("Default").assertExists()
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

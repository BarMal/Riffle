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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
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
    fun libraryPageDispatchesLibraryViewAndGridActions() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            launcherSettings =
                                LauncherSettings(
                                    appDrawer =
                                        AppDrawerSettings(
                                            libraryViews = mapOf(HomeLayoutDeviceClass.PHONE to LibraryView.Grid),
                                            iconGridColumns = 4,
                                        ),
                                ),
                        ).settingsSurfaceState(),
                    page = SettingsPage.LIBRARY,
                    onPageSelected = {},
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag("${LIBRARY_VIEW_TEST_TAG_PREFIX}AlphaList").performClick()
        composeRule.onNodeWithTag("${APP_DRAWER_GRID_COLUMNS_TEST_TAG_PREFIX}6").performClick()

        assertEquals(
            listOf(
                LauncherShellAction.SelectLibraryView(LibraryView.AlphaList, HomeLayoutDeviceClass.PHONE),
                LauncherShellAction.SelectAppDrawerIconGridColumns(columns = 6),
            ),
            actions,
        )
    }

    @Test
    fun hiddenBarsShowCheckedIndependentControlsThatDispatchActions() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            launcherSettings =
                                LauncherSettings(
                                    appearance =
                                        AppearanceSettings(
                                            hideStatusBarOnHome = true,
                                            hideNavigationBarOnHome = true,
                                        ),
                                ),
                        ).settingsSurfaceState(),
                    page = SettingsPage.HOME,
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
                    page = SettingsPage.PRIVACY,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Default home app").assertExists()
        composeRule.onNodeWithText("Notification access").assertExists()
        composeRule.onNodeWithText("Floating dock access").assertExists()
    }

    @Test
    fun mainPageMakesNotificationAccessDiscoverable() {
        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                            notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                            overlayDockPermissionStatus = OverlayDockPermissionStatus.GRANTED,
                        ).settingsSurfaceState(),
                    page = SettingsPage.MAIN,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Privacy and access").assertExists()
        composeRule
            .onNodeWithText("Notifications not allowed · Home set · Floating dock allowed")
            .assertExists()
    }

    @Test
    fun motionPageKeepsHapticsWithAccessibilityControls() {
        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state = LauncherShellState().settingsSurfaceState(),
                    page = SettingsPage.MOTION,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Reduced motion").assertExists()
        composeRule.onNodeWithText("Feedback strength").assertExists()
    }

    @Test
    fun permissionsPageSurfacesRecoveryActionsForMissingAccess() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                            overlayDockPermissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
                        ).settingsSurfaceState(),
                    page = SettingsPage.PRIVACY,
                    onPageSelected = {},
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Allow notification access").performClick()
        composeRule.onNodeWithText("Allow overlay access").performClick()

        assertEquals(
            listOf(
                LauncherShellAction.RequestNotificationAccess,
                LauncherShellAction.RequestOverlayDockPermission,
            ),
            actions,
        )
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

        // The default home app lives only on Privacy and access now; the main page no longer repeats it.
        listOf(SettingsPage.PRIVACY).forEach { page ->
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
                    page = SettingsPage.PRIVACY,
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

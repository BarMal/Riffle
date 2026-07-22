package com.riffle.app.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardModeGuardedSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherShellRoutesCardModeToTimeScapeAndRecoversToStandard() {
        var state by mutableStateOf(cardsState(NotificationAccessStatus.GRANTED))

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                timeScapeWindowLayout = TimeScapeWindowLayout(widthDp = 0, heightDp = 0),
                onAction = {},
            )
        }

        composeRule
            .onNodeWithText("No active stages yet. New notifications will appear here.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.REVOKED)
        }

        composeRule
            .onNodeWithText("Notification access was revoked. Restore access to update stages.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()

        composeRule.runOnIdle {
            state = standardState()
        }

        composeRule
            .onNodeWithText("Notification access was revoked. Restore access to update stages.")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Allow access").assertDoesNotExist()
    }

    @Test
    fun enteringCardsShowsMissingAccessRecoveryWhenPlatformWindowMetricsAreNotReady() {
        var state by mutableStateOf(standardState().copy(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED))

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                timeScapeWindowLayout = TimeScapeWindowLayout(widthDp = 0, heightDp = 0),
                onAction = {},
            )
        }

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.UNKNOWN)
        }

        composeRule
            .onNodeWithText("Checking notification access.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.NOT_GRANTED)
        }

        composeRule
            .onNodeWithText("Allow notification access to show your app stages.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()
    }

    @Test
    fun launcherShellCardModeShowsTheFocusedTimeScapeCardStack() {
        composeRule.setContent {
            LauncherShellContent(
                state = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup())),
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Welcome").assertExists()
    }

    @Test
    fun cardModeKeepsTheStandardDockAndContextActionsAvailable() {
        val app = cardsHomeApp()
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera"),
                appIdentity = app.identity,
                label = app.label,
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    dock = standard.dock.copy(items = listOf(shortcut)),
                )
            }
        val state =
            LauncherShellState(
                firstRunStatus = FirstRunStatus.COMPLETE,
                homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                homeLayout = layout,
                homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                installedApps = listOf(app),
                profileContentVisibility = mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            )

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onNodeWithTag(dockItemTestTag(shortcut.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(dockItemTestTag(shortcut.id)).performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove from dock").assertIsDisplayed()
    }

    @Test
    fun cardModeWithoutStagesKeepsHomeShortcutLongPressAndDragAvailable() {
        val app = cardsHomeApp()
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera-home"),
                appIdentity = app.identity,
                label = app.label,
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    pages = standard.pages.map { page -> page.copy(items = listOf(shortcut)) },
                )
            }
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.COMPLETE,
                        homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                        homeLayout = layout,
                        homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                        notificationAccessStatus = NotificationAccessStatus.GRANTED,
                        installedApps = listOf(app),
                        profileContentVisibility =
                            mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
                    ),
                appIconLoader = EmptyAppIconLoader,
                onAction = actions::add,
            )
        }

        composeRule.onNodeWithText(app.label).assertIsDisplayed()
        composeRule.onNodeWithText(app.label).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(160f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(actions.single() is LauncherShellAction.MoveHomeShortcutToCell)
            actions.clear()
        }

        composeRule.onNodeWithText(app.label).performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove from home").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.RemoveHomeShortcut(shortcut.id)), actions)
        }
    }

    @Test
    fun cardModeDockReceivesPhysicalTapWithAnActiveTimeScapeStage() {
        val app = cardsHomeApp()
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera"),
                appIdentity = app.identity,
                label = app.label,
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    dock = standard.dock.copy(items = listOf(shortcut)),
                )
            }
        val actions = mutableListOf<LauncherShellAction>()
        val stageState = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup()))
        val state =
            stageState.copy(
                homeLayout = layout,
                homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                installedApps = stageState.installedApps + app,
                profileContentVisibility =
                    stageState.profileContentVisibility +
                        (app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            )

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                timeScapeWindowLayout = TimeScapeWindowLayout(widthDp = 360, heightDp = 640),
                onAction = actions::add,
            )
        }

        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithTag(dockItemTestTag(shortcut.id)).performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.LaunchApp(app.identity)), actions)
        }
    }

    @Test
    fun cardModeExpandedDockShelfWithBottomMarginReceivesPhysicalTapWithAnActiveTimeScapeStage() {
        val primary = cardsHomeApp(packageName = "com.example.camera")
        val overflow = cardsHomeApp(packageName = "com.example.photos")
        val primaryShortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera"),
                appIdentity = primary.identity,
                label = primary.label,
            )
        val overflowShortcut =
            AppShortcutItem(
                id = LauncherItemId("app:photos"),
                appIdentity = overflow.identity,
                label = overflow.label,
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    settings =
                        standard.settings.copy(
                            grid =
                                standard.settings.grid.copy(
                                    margin = standard.settings.grid.margin.copy(bottom = 48),
                                ),
                        ),
                    dock =
                        standard.dock.copy(
                            capacity = 1,
                            items = listOf(primaryShortcut, overflowShortcut),
                        ),
                )
            }
        val actions = mutableListOf<LauncherShellAction>()
        val stageState = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup()))
        val state =
            stageState.copy(
                homeLayout = layout,
                homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                installedApps = stageState.installedApps + listOf(primary, overflow),
                profileContentVisibility =
                    stageState.profileContentVisibility +
                        (primary.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            )

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                timeScapeWindowLayout = TimeScapeWindowLayout(widthDp = 360, heightDp = 640),
                onAction = actions::add,
            )
        }

        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithTag(dockItemTestTag(primaryShortcut.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(dockItemTestTag(overflowShortcut.id)).performTouchInput {
            click(Offset(width / 2f, 1f))
        }

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.LaunchApp(overflow.identity)), actions)
        }
    }

    @Test
    fun cardModeRendersOneDockAndOpensDockFolders() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = emptyList(),
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    dock = standard.dock.copy(items = listOf(folder)),
                )
            }

        composeRule.setContent {
            LauncherShellContent(
                state =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.COMPLETE,
                        homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                        homeLayout = layout,
                        homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                        notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    ),
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onAllNodesWithTag(HOME_DOCK_TEST_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(dockItemTestTag(folder.id)).performClick()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun cardModeKeepsTimeScapeCardDetailsInteractiveAlongsideTheDock() {
        composeRule.setContent {
            LauncherShellContent(
                state = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup())),
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
    }

    @Test
    fun cardsSurfaceUsesEffectiveSystemBarInsetPolicy() {
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = false, reserveNavigationBar = false),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    fullscreenHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = false, reserveNavigationBar = true),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    hideStatusBarOnHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = true, reserveNavigationBar = false),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    hideNavigationBarOnHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = true, reserveNavigationBar = true),
            cardsPanelInsetPolicy(cardsState(NotificationAccessStatus.GRANTED)),
        )
    }

    private fun cardsState(
        notificationAccessStatus: NotificationAccessStatus,
        groups: List<AppNotificationGroup> = emptyList(),
        fullscreenHome: Boolean = false,
        hideStatusBarOnHome: Boolean = false,
        hideNavigationBarOnHome: Boolean = false,
    ): LauncherShellState {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        return LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
            notificationAccessStatus = notificationAccessStatus,
            installedApps =
                groups.map { group ->
                    InstalledApp(
                        identity =
                            AppIdentity(
                                packageName = group.packageName,
                                activityName = AppActivityName(".Main"),
                                profile = AppProfile.personal(),
                            ),
                        label = "Messages",
                    )
                },
            profileContentVisibility =
                groups.associate { group ->
                    group.profileId to AppProfileContentVisibility.VISIBLE
                },
            notificationGroupsByApp = groups,
            notificationCountsByCategory = mapOf(NotificationCategory.MESSAGE to groups.sumOf { group -> group.count }),
            launcherSettings =
                LauncherSettings(
                    appearance =
                        AppearanceSettings(
                            fullscreenHome = fullscreenHome,
                            hideStatusBarOnHome = hideStatusBarOnHome,
                            hideNavigationBarOnHome = hideNavigationBarOnHome,
                        ),
                ),
        )
    }

    private fun standardState(): LauncherShellState {
        val layout = HomeLayoutDefaults.standard()
        return LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
        )
    }

    private fun notificationGroup(): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName("com.example.messages"),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.NOW,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("messages:welcome"),
                        packageName = AppPackageName("com.example.messages"),
                        category = NotificationCategory.MESSAGE,
                        title = "Welcome",
                        text = "Your first card is ready",
                        postedAtEpochMillis = 1L,
                    ),
                ),
        )

    private fun cardsHomeApp(packageName: String = "com.example.camera"): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(".Camera"),
                    profile = AppProfile.personal(),
                ),
            label = packageName.substringAfterLast('.').replaceFirstChar(Char::uppercase),
        )
}

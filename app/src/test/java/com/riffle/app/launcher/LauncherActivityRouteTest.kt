package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.FolderItemMoveDirection
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherActivityRouteTest {
    @Test
    fun routesDefaultHomeRequest() {
        assertEquals(
            LauncherActivityRoute.RequestDefaultHome,
            LauncherShellAction.RequestDefaultHome.launcherActivityRoute(),
        )
    }

    @Test
    fun routesOpenDefaultHome() {
        assertEquals(
            LauncherActivityRoute.OpenDefaultHome,
            LauncherShellAction.OpenDefaultHome.launcherActivityRoute(),
        )
    }

    @Test
    fun routesNavigationActions() {
        val actions =
            mapOf(
                LauncherShellAction.OpenHome to ShellNavigationAction.OpenHome,
                LauncherShellAction.OpenAppDrawer to ShellNavigationAction.OpenAppDrawer,
                LauncherShellAction.OpenSearch to ShellNavigationAction.OpenSearch,
                LauncherShellAction.OpenNotifications to ShellNavigationAction.OpenNotifications,
                LauncherShellAction.OpenSettings to ShellNavigationAction.OpenSettings,
                LauncherShellAction.OpenSettingsPage(SettingsPage.APPEARANCE) to ShellNavigationAction.OpenSettings,
            )

        actions.forEach { (action, navigationAction) ->
            assertEquals(
                LauncherActivityRoute.Navigation(navigationAction),
                action.launcherActivityRoute(),
            )
        }
    }

    @Test
    fun routesHomePageEditActions() {
        assertEquals(
            LauncherActivityRoute.HomePageEdit,
            LauncherShellAction.SelectSelectedHomePageGridDimensions(GridDimensions(columns = 5, rows = 6))
                .launcherActivityRoute(),
        )
    }

    @Test
    fun routesHomeShortcutEditActions() {
        val actions =
            listOf(
                LauncherShellAction.RemoveHomeShortcut(LauncherItemId("shortcut")),
                LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
                LauncherShellAction.CreateHomeFolder(
                    itemIds = listOf(LauncherItemId("one")),
                    label = "Folder",
                ),
                LauncherShellAction.RenameHomeFolder(
                    itemId = LauncherItemId("folder"),
                    label = "Renamed",
                ),
                LauncherShellAction.AddAppToFolder(
                    folderId = LauncherItemId("folder"),
                    app = installedApp,
                ),
                LauncherShellAction.RemoveAppFromFolder(
                    folderId = LauncherItemId("folder"),
                    itemId = LauncherItemId("shortcut"),
                ),
                LauncherShellAction.MoveAppInFolder(
                    folderId = LauncherItemId("folder"),
                    itemId = LauncherItemId("shortcut"),
                    direction = FolderItemMoveDirection.DOWN,
                ),
                LauncherShellAction.MoveAppOutOfFolder(
                    folderId = LauncherItemId("folder"),
                    itemId = LauncherItemId("shortcut"),
                ),
                LauncherShellAction.MoveHomeShortcutToCell(
                    itemId = LauncherItemId("shortcut"),
                    cell = GridCell(column = 1, row = 2),
                ),
                LauncherShellAction.AddAppShortcutToHome(appShortcut),
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(42),
                    label = "Weather",
                ),
                LauncherShellAction.ResizeHomeWidget(
                    itemId = LauncherItemId("widget"),
                    span = GridSpan(columns = 2, rows = 2),
                ),
            )

        actions.forEach { action ->
            assertEquals(LauncherActivityRoute.HomeShortcutEdit, action.launcherActivityRoute())
        }
    }

    @Test
    fun routesDockEditActions() {
        val actions =
            listOf(
                LauncherShellAction.AddAppToDock(installedApp),
                LauncherShellAction.SelectDockEnabled(enabled = true),
                LauncherShellAction.SelectDockCapacity(capacity = 6),
                LauncherShellAction.SelectDockIconSize(sizeDp = 52),
                LauncherShellAction.SelectDockBackgroundAlpha(alphaPercent = 80),
                LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED),
                LauncherShellAction.SelectDockItemSpacing(spacingDp = 12),
                LauncherShellAction.RemoveDockShortcut(LauncherItemId("dock")),
                LauncherShellAction.MoveDockShortcut(
                    itemId = LauncherItemId("dock"),
                    direction = DockItemMoveDirection.RIGHT,
                ),
            )

        actions.forEach { action ->
            assertEquals(LauncherActivityRoute.DockEdit, action.launcherActivityRoute())
        }
    }

    @Test
    fun ignoresActionsHandledByOtherGateways() {
        assertNull(LauncherShellAction.LaunchApp(appIdentity).launcherActivityRoute())
        assertNull(LauncherShellAction.ExportLauncherBackup.launcherActivityRoute())
        assertNull(LauncherShellAction.DismissNotifications(emptyList()).launcherActivityRoute())
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
        val installedApp = InstalledApp(identity = appIdentity, label = "Example")
        val appShortcut =
            AppShortcut(
                id = AppShortcutId("shortcut"),
                appIdentity = appIdentity,
                shortLabel = "Shortcut",
            )
    }
}

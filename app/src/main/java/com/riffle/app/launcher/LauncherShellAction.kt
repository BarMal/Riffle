package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeShortcutMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenNotifications : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    data object RequestNotificationAccess : LauncherShellAction

    data object EnterHomeEditMode : LauncherShellAction

    data object ExitHomeEditMode : LauncherShellAction

    data object EnterHomePageOverview : LauncherShellAction

    data object AddHomePage : LauncherShellAction

    data object DuplicateSelectedHomePage : LauncherShellAction

    data class SelectHomePage(val pageId: LauncherPageId) : LauncherShellAction

    data object SelectPreviousHomePage : LauncherShellAction

    data object SelectNextHomePage : LauncherShellAction

    data object MoveSelectedHomePageLeft : LauncherShellAction

    data object MoveSelectedHomePageRight : LauncherShellAction

    data object DeleteSelectedHomePage : LauncherShellAction

    data class SelectHomeGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectLauncherViewMode(val mode: LauncherViewMode) : LauncherShellAction

    data class SelectHomeLayoutDeviceClass(val deviceClass: HomeLayoutDeviceClass) : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class LaunchAppShortcut(val shortcut: AppShortcut) : LauncherShellAction

    data class OpenAppInfo(val identity: AppIdentity) : LauncherShellAction

    data class HideApp(val identity: AppIdentity) : LauncherShellAction

    data class UnhideApp(val identity: AppIdentity) : LauncherShellAction

    data class AddAppToHome(val app: InstalledApp) : LauncherShellAction

    data class AddAppShortcutToHome(val shortcut: AppShortcut) : LauncherShellAction

    data class AddAppToDock(val app: InstalledApp) : LauncherShellAction

    data class AppDrawerQueryChanged(val query: String) : LauncherShellAction

    data class AppDrawerProfileFilterSelected(
        val filter: AppDrawerProfileFilter,
    ) : LauncherShellAction

    data class RemoveHomeShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class CreateHomeFolder(
        val itemIds: List<LauncherItemId>,
        val label: String,
    ) : LauncherShellAction

    data class RenameHomeFolder(
        val itemId: LauncherItemId,
        val label: String,
    ) : LauncherShellAction

    data class AddAppToFolder(
        val folderId: LauncherItemId,
        val app: InstalledApp,
    ) : LauncherShellAction

    data class RemoveAppFromFolder(
        val folderId: LauncherItemId,
        val itemId: LauncherItemId,
    ) : LauncherShellAction

    data class RemoveDockShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class MoveDockShortcut(
        val itemId: LauncherItemId,
        val direction: DockItemMoveDirection,
    ) : LauncherShellAction

    data class MoveHomeShortcut(
        val itemId: LauncherItemId,
        val direction: HomeShortcutMoveDirection,
    ) : LauncherShellAction

    data class SearchQueryChanged(val query: String) : LauncherShellAction

    data class SearchProfileFilterSelected(
        val filter: AppDrawerProfileFilter,
    ) : LauncherShellAction

    data class SelectWallpaperSource(val source: WallpaperSource) : LauncherShellAction

    data class SelectHomeSwipeGestureAction(
        val direction: HomeSwipeGestureDirection,
        val action: LauncherGestureAction,
    ) : LauncherShellAction

    data object ResetHomeSwipeGestureActions : LauncherShellAction

    data class DismissNotifications(val keys: List<LauncherNotificationKey>) : LauncherShellAction
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.HomeShortcutMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WallpaperSource

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    data object RequestNotificationAccess : LauncherShellAction

    data object EnterHomeEditMode : LauncherShellAction

    data object ExitHomeEditMode : LauncherShellAction

    data object AddHomePage : LauncherShellAction

    data object SelectPreviousHomePage : LauncherShellAction

    data object SelectNextHomePage : LauncherShellAction

    data object MoveSelectedHomePageLeft : LauncherShellAction

    data object MoveSelectedHomePageRight : LauncherShellAction

    data object DeleteSelectedHomePage : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class AddAppToHome(val app: InstalledApp) : LauncherShellAction

    data class AddAppToDock(val app: InstalledApp) : LauncherShellAction

    data class RemoveHomeShortcut(val itemId: LauncherItemId) : LauncherShellAction

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

    data class SelectWallpaperSource(val source: WallpaperSource) : LauncherShellAction
}

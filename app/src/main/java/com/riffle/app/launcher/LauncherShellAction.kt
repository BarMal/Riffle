package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.LauncherItemId

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object CompleteFirstRun : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    data object EnterHomeEditMode : LauncherShellAction

    data object ExitHomeEditMode : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class AddAppToHome(val app: InstalledApp) : LauncherShellAction

    data class RemoveHomeShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class SearchQueryChanged(val query: String) : LauncherShellAction
}

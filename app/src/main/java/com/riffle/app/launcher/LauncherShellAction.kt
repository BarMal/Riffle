package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object CompleteFirstRun : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class AddAppToHome(val app: InstalledApp) : LauncherShellAction

    data class SearchQueryChanged(val query: String) : LauncherShellAction
}

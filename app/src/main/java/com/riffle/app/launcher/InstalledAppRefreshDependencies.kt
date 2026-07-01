package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

internal data class InstalledAppRefreshDependencies(
    val installedAppRepository: InstalledAppRepository,
    val appVisibilityRepository: AppVisibilityRepository,
    val appCatalog: InstalledAppCatalog,
    val homeLayoutRepository: HomeLayoutRepository,
    val appShortcutRepository: AppShortcutRepository,
)

internal fun LauncherShellAction.applyAppVisibilityAction(appVisibilityRepository: AppVisibilityRepository) {
    when (this) {
        is LauncherShellAction.HideApp -> appVisibilityRepository.hideApp(identity)
        is LauncherShellAction.UnhideApp -> appVisibilityRepository.showApp(identity)
        else -> Unit
    }
}

internal fun LauncherShellState.withRefreshedInstalledApps(deps: InstalledAppRefreshDependencies): LauncherShellState =
    withInstalledApps(
        installedAppRepository = deps.installedAppRepository,
        appVisibilityRepository = deps.appVisibilityRepository,
        appCatalog = deps.appCatalog,
    )
        .withoutUnavailableApps(deps.homeLayoutRepository)
        .withHomeScreenLibraryApps(deps.homeLayoutRepository)
        .withAppShortcuts(deps.appShortcutRepository, deps.appCatalog)

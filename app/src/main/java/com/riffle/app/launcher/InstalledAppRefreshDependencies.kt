package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
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
    when (val result = deps.installedAppRepository.refreshResult()) {
        is InstalledAppRefreshResult.Authoritative ->
            withInstalledApps(
                apps = result.apps,
                appVisibilityRepository = deps.appVisibilityRepository,
                appCatalog = deps.appCatalog,
            )
                .withHomeScreenLibraryApps(deps.homeLayoutRepository)
                .withRefreshedGeneratedPages(deps.homeLayoutRepository)
                .withAppShortcuts(deps.appShortcutRepository, deps.appCatalog)
                .withAuthoritativeProfileContentVisibility(result.profileContentVisibility)

        is InstalledAppRefreshResult.Partial ->
            withPartialProfileContentVisibility(result.profileContentVisibility)

        InstalledAppRefreshResult.Unavailable,
        -> this
    }

private fun LauncherShellState.withAuthoritativeProfileContentVisibility(
    refreshedVisibility: Map<AppProfileId, AppProfileContentVisibility>,
): LauncherShellState = copy(profileContentVisibility = refreshedVisibility)

private fun LauncherShellState.withPartialProfileContentVisibility(
    refreshedVisibility: Map<AppProfileId, AppProfileContentVisibility>,
): LauncherShellState =
    if (refreshedVisibility.isEmpty()) {
        this
    } else {
        copy(profileContentVisibility = profileContentVisibility + refreshedVisibility)
    }

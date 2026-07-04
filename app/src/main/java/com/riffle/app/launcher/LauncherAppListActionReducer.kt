package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog

internal class LauncherAppListActionReducer(
    private val appCatalog: InstalledAppCatalog,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState? =
        when (action) {
            is LauncherShellAction.AppDrawerQueryChanged ->
                state.copy(
                    appDrawerQuery = action.query,
                    appDrawerApps =
                        appCatalog.drawerApps(
                            apps = state.installedApps,
                            query = action.query,
                            profileFilter = state.appDrawerProfileFilter,
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.AppDrawerProfileFilterSelected ->
                state.copy(
                    appDrawerProfileFilter = action.filter,
                    appDrawerApps =
                        appCatalog.drawerApps(
                            apps = state.installedApps,
                            query = state.appDrawerQuery,
                            profileFilter = action.filter,
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.SearchQueryChanged ->
                state.copy(
                    searchQuery = action.query,
                    searchResults =
                        appCatalog.filteredApps(
                            apps = state.installedApps,
                            query = action.query,
                            profileFilter = state.searchProfileFilter,
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.SearchProfileFilterSelected ->
                state.copy(
                    searchProfileFilter = action.filter,
                    searchResults =
                        appCatalog.filteredApps(
                            apps = state.installedApps,
                            query = state.searchQuery,
                            profileFilter = action.filter,
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            else -> null
        }
}

internal fun LauncherShellState.withFilteredApps(appCatalog: InstalledAppCatalog): LauncherShellState =
    appDrawerProfileFilter.coerceAvailableFor(installedApps).let { availableDrawerFilter ->
        searchProfileFilter.coerceAvailableFor(installedApps).let { availableSearchFilter ->
            copy(
                appDrawerProfileFilter = availableDrawerFilter,
                searchProfileFilter = availableSearchFilter,
                appDrawerApps =
                    appCatalog.drawerApps(
                        apps = installedApps,
                        query = appDrawerQuery,
                        profileFilter = availableDrawerFilter,
                        appShortcutsByApp = appShortcutsByApp,
                    ),
                searchResults =
                    appCatalog.filteredApps(
                        apps = installedApps,
                        query = searchQuery,
                        profileFilter = availableSearchFilter,
                        appShortcutsByApp = appShortcutsByApp,
                    ),
            )
        }
    }

internal fun InstalledAppCatalog.drawerApps(
    apps: List<InstalledApp>,
    query: String,
    profileFilter: AppDrawerProfileFilter,
    appShortcutsByApp: AppShortcutsByApp,
): List<InstalledApp> =
    filteredApps(
        apps = apps,
        query = query,
        profileFilter = profileFilter,
        appShortcutsByApp = appShortcutsByApp,
    )

internal fun InstalledAppCatalog.filteredApps(
    apps: List<InstalledApp>,
    query: String,
    profileFilter: AppDrawerProfileFilter,
    appShortcutsByApp: AppShortcutsByApp,
): List<InstalledApp> =
    searchApps(
        apps = apps,
        query = query,
        shortcutsByApp = appShortcutsByApp,
    )
        .filter { app -> app.matches(profileFilter) }

private fun InstalledApp.matches(profileFilter: AppDrawerProfileFilter): Boolean =
    when (profileFilter) {
        AppDrawerProfileFilter.ALL -> true
        AppDrawerProfileFilter.PERSONAL -> identity.profile.type == AppProfileType.PERSONAL
        AppDrawerProfileFilter.WORK -> identity.profile.type == AppProfileType.WORK
        AppDrawerProfileFilter.PRIVATE -> identity.profile.type == AppProfileType.PRIVATE
    }

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileSelection
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.filterByProfile
import com.riffle.core.domain.launcher.apps.matches
import com.riffle.core.domain.launcher.apps.toProfileSelection

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
                state.withSearchQuery(
                    query = action.query,
                    appCatalog = appCatalog,
                )

            is LauncherShellAction.SearchProfileFilterSelected ->
                state.copy(
                    searchProfileFilter = action.filter,
                ).withSearchFilters(
                    filters = state.searchFilters.withProfileFilter(action.filter),
                    appCatalog = appCatalog,
                )

            is LauncherShellAction.ToggleSearchContentFilter ->
                state.withSearchFilters(
                    filters = state.searchFilters.withToggledContent(action.filter),
                    appCatalog = appCatalog,
                )

            is LauncherShellAction.ToggleSearchProfileFilter ->
                state.withSearchFilters(
                    filters = state.searchFilters.withToggledProfile(action.profileType),
                    appCatalog = appCatalog,
                )

            LauncherShellAction.ResetSearchFilters ->
                state.withSearchFilters(
                    filters = AppSearchFilters(),
                    appCatalog = appCatalog,
                )

            else -> null
        }
}

internal fun LauncherShellState.withFilteredApps(appCatalog: InstalledAppCatalog): LauncherShellState =
    appDrawerProfileFilter.let { drawerFilter ->
        searchProfileFilter.coerceAvailableFor(installedApps).let { availableSearchFilter ->
            val availableSearchFilters = searchFilters.coerceProfilesAvailableFor(installedApps)
            copy(
                appDrawerProfileFilter = drawerFilter,
                searchProfileFilter = availableSearchFilter,
                searchFilters = availableSearchFilters,
                appDrawerApps =
                    appCatalog.drawerApps(
                        apps = installedApps,
                        query = appDrawerQuery,
                        profileFilter = drawerFilter,
                        appShortcutsByApp = appShortcutsByApp,
                    ),
                searchResults =
                    appCatalog.filteredApps(
                        apps = installedApps,
                        query = searchQuery,
                        filters = availableSearchFilters,
                    ),
                searchShortcutResults =
                    searchShortcutResults(
                        query = searchQuery,
                        filters = availableSearchFilters,
                    ),
                searchSettingsResults = searchSettingsResults(searchQuery),
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
        .filter { app -> profileFilter.matches(app) }

internal fun InstalledAppCatalog.filteredApps(
    apps: List<InstalledApp>,
    query: String,
    filters: AppSearchFilters,
): List<InstalledApp> {
    val profileApps = apps.filterByProfile(filters.toProfileSelection())
    val appMatches =
        if (AppSearchContentFilter.APPS in filters.content) {
            searchApps(
                apps = profileApps,
                query = query,
                shortcutsByApp = emptyMap(),
            )
        } else {
            emptyList()
        }

    return appMatches.distinctBy { app -> app.identity }
}

private fun LauncherShellState.withSearchQuery(
    query: String,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    copy(searchQuery = query)
        .withSearchResults(
            query = query,
            filters = searchFilters,
            appCatalog = appCatalog,
        )

private fun LauncherShellState.withSearchFilters(
    filters: AppSearchFilters,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    copy(searchFilters = filters)
        .withSearchResults(
            query = searchQuery,
            filters = filters,
            appCatalog = appCatalog,
        )

private fun LauncherShellState.withSearchResults(
    query: String,
    filters: AppSearchFilters,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    copy(
        searchResults =
            appCatalog.filteredApps(
                apps = installedApps,
                query = query,
                filters = filters,
            ),
        searchShortcutResults =
            searchShortcutResults(
                query = query,
                filters = filters,
            ),
        searchSettingsResults = searchSettingsResults(query),
    )

private fun AppSearchFilters.withProfileFilter(filter: AppDrawerProfileFilter): AppSearchFilters =
    copy(profiles = filter.toProfileSelection().types)

private fun AppSearchFilters.toProfileSelection() = AppProfileSelection(types = profiles)

private fun AppSearchFilters.coerceProfilesAvailableFor(apps: List<InstalledApp>): AppSearchFilters {
    val availableProfiles = apps.map { app -> app.identity.profile.type }.toSet()
    val selectedAvailableProfiles = profiles.intersect(availableProfiles)
    val coercedProfiles =
        when {
            selectedAvailableProfiles.isNotEmpty() -> selectedAvailableProfiles
            AppProfileType.PERSONAL in availableProfiles -> setOf(AppProfileType.PERSONAL)
            else -> availableProfiles
        }

    return copy(profiles = coercedProfiles)
}

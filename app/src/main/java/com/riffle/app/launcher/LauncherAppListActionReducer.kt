package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
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
                            filters = state.searchFilters,
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.SearchProfileFilterSelected ->
                state.copy(
                    searchProfileFilter = action.filter,
                    searchFilters = state.searchFilters.withProfileFilter(action.filter),
                    searchResults =
                        appCatalog.filteredApps(
                            apps = state.installedApps,
                            query = state.searchQuery,
                            filters = state.searchFilters.withProfileFilter(action.filter),
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.ToggleSearchContentFilter ->
                state.copy(
                    searchFilters = state.searchFilters.withToggledContent(action.filter),
                    searchResults =
                        appCatalog.filteredApps(
                            apps = state.installedApps,
                            query = state.searchQuery,
                            filters = state.searchFilters.withToggledContent(action.filter),
                            appShortcutsByApp = state.appShortcutsByApp,
                        ),
                )

            is LauncherShellAction.ToggleSearchProfileFilter ->
                state.copy(
                    searchFilters = state.searchFilters.withToggledProfile(action.profileType),
                    searchResults =
                        appCatalog.filteredApps(
                            apps = state.installedApps,
                            query = state.searchQuery,
                            filters = state.searchFilters.withToggledProfile(action.profileType),
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
                        filters = searchFilters,
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

internal fun InstalledAppCatalog.filteredApps(
    apps: List<InstalledApp>,
    query: String,
    filters: AppSearchFilters,
    appShortcutsByApp: AppShortcutsByApp,
): List<InstalledApp> {
    val profileApps = apps.filter { app -> app.identity.profile.type in filters.profiles }
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
    val shortcutMatches =
        if (AppSearchContentFilter.SHORTCUTS in filters.content && query.isNotBlank()) {
            profileApps.filter { app -> appShortcutsByApp[app.identity].orEmpty().matchesSearchQuery(query) }
        } else {
            emptyList()
        }

    return (appMatches + shortcutMatches).distinctBy { app -> app.identity }
}

private fun AppSearchFilters.withProfileFilter(filter: AppDrawerProfileFilter): AppSearchFilters =
    when (filter) {
        AppDrawerProfileFilter.ALL -> copy(profiles = AppProfileType.entries.toSet())
        AppDrawerProfileFilter.PERSONAL -> copy(profiles = setOf(AppProfileType.PERSONAL))
        AppDrawerProfileFilter.WORK -> copy(profiles = setOf(AppProfileType.WORK))
        AppDrawerProfileFilter.PRIVATE -> copy(profiles = setOf(AppProfileType.PRIVATE))
    }

private fun List<AppShortcut>.matchesSearchQuery(query: String): Boolean {
    val queryTokens = query.normalizedSearchTokens()
    if (queryTokens.isEmpty()) {
        return false
    }

    return any { shortcut ->
        listOf(
            shortcut.shortLabel,
            shortcut.longLabel.orEmpty(),
            shortcut.id.value,
        ).any { candidate -> candidate.lowercase().matchesAll(queryTokens) }
    }
}

private fun String.normalizedSearchTokens(): List<String> =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

private fun String.matchesAll(queryTokens: List<String>): Boolean {
    return queryTokens.all { queryToken -> contains(queryToken) }
}

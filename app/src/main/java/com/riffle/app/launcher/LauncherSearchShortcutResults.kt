package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut

internal fun LauncherShellState.searchShortcutResults(
    query: String,
    filters: AppSearchFilters,
): List<AppShortcut> {
    if (AppSearchContentFilter.SHORTCUTS !in filters.content || query.isBlank()) {
        return emptyList()
    }

    val visibleAppIdentities =
        installedApps
            .filter { app -> app.identity.profile.type in filters.profiles }
            .map { app -> app.identity }
            .toSet()

    return visibleAppIdentities
        .flatMap { identity -> appShortcutsByApp[identity].orEmpty() }
        .filter { shortcut -> shortcut.enabled }
        .filter { shortcut -> shortcut.matchesSearchQuery(query) }
        .sortedWith(
            compareBy<AppShortcut> { shortcut -> shortcut.shortLabel.lowercase() }
                .thenBy { shortcut -> shortcut.appIdentity.packageName.value }
                .thenBy { shortcut -> shortcut.id.value },
        )
}

private fun AppShortcut.matchesSearchQuery(query: String): Boolean {
    val queryTokens = query.normalizedSearchTokens()
    if (queryTokens.isEmpty()) {
        return false
    }

    return listOf(
        shortLabel,
        longLabel.orEmpty(),
        id.value,
    ).any { candidate -> candidate.lowercase().matchesAll(queryTokens) }
}

private fun String.normalizedSearchTokens(): List<String> =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

private fun String.matchesAll(queryTokens: List<String>): Boolean {
    return queryTokens.all { queryToken -> contains(queryToken) }
}

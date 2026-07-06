package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.search.containsAllSearchTokens
import com.riffle.core.domain.launcher.search.normalizedSearchTokens
import com.riffle.core.domain.launcher.search.searchAcronym

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
    val queryTokens = normalizedSearchTokens(query)
    if (queryTokens.isEmpty()) {
        return false
    }

    return listOf(
        shortLabel,
        longLabel.orEmpty(),
        id.value,
    )
        .flatMap { candidate ->
            val normalizedCandidate = candidate.lowercase()
            listOf(normalizedCandidate, normalizedCandidate.searchAcronym())
        }
        .any { candidate -> candidate.containsAllSearchTokens(queryTokens) }
}

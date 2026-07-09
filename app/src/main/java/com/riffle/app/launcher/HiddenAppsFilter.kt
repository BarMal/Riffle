package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.matches
import com.riffle.core.domain.launcher.search.containsAllSearchTokens
import com.riffle.core.domain.launcher.search.normalizedSearchTokens
import com.riffle.core.domain.launcher.search.searchAcronym

internal fun List<InstalledApp>.filteredHiddenApps(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): List<InstalledApp> {
    val tokens = normalizedSearchTokens(query)

    return filter { app ->
        profileFilter.matches(app) && app.matchesHiddenAppSearch(tokens)
    }
}

internal fun hiddenAppsEmptyText(
    totalHiddenAppCount: Int,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL -> "No matching hidden apps"
        totalHiddenAppCount == 0 -> "No hidden apps"
        else -> "No hidden apps"
    }

internal fun hiddenAppsSummaryText(
    totalHiddenAppCount: Int,
    resultCount: Int,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL ->
            "${resultCount.appCountLabel()} matching${profileFilter.matchingProfileSuffix()}, " +
                "${totalHiddenAppCount.appCountLabel()} hidden"

        else -> "${totalHiddenAppCount.appCountLabel()} hidden"
    }

internal fun shouldShowHiddenAppsClearFilters(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): Boolean = query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL

private fun InstalledApp.matchesHiddenAppSearch(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true

    val searchableValues =
        listOfNotNull(
            label,
            label.searchAcronym(),
            identity.packageName.value,
            identity.activityName.value,
            identity.profile.id.value,
            identity.profile.type.name,
            identity.profile.drawerProfilePrefix(),
        ).map { value -> value.lowercase() }

    return searchableValues.containsAllSearchTokens(tokens)
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp

internal fun List<InstalledApp>.filteredHiddenApps(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): List<InstalledApp> {
    val tokens = query.normalizedHiddenAppSearchTokens()

    return filter { app ->
        app.matchesHiddenAppProfileFilter(profileFilter) && app.matchesHiddenAppSearch(tokens)
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

private fun InstalledApp.matchesHiddenAppProfileFilter(profileFilter: AppDrawerProfileFilter): Boolean =
    when (profileFilter) {
        AppDrawerProfileFilter.ALL -> true
        AppDrawerProfileFilter.PERSONAL -> identity.profile.type == AppProfileType.PERSONAL
        AppDrawerProfileFilter.WORK -> identity.profile.type == AppProfileType.WORK
        AppDrawerProfileFilter.PRIVATE -> identity.profile.type == AppProfileType.PRIVATE
    }

private fun InstalledApp.matchesHiddenAppSearch(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true

    val searchableValues =
        listOfNotNull(
            label,
            identity.packageName.value,
            identity.activityName.value,
            identity.profile.id.value,
            identity.profile.type.name,
            identity.profile.drawerProfilePrefix(),
        ).map { value -> value.lowercase() }

    return tokens.all { token -> searchableValues.any { value -> value.contains(token) } }
}

private fun String.normalizedHiddenAppSearchTokens(): List<String> =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

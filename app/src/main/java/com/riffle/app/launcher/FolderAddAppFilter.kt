package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.matches
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.search.containsAllSearchTokens
import com.riffle.core.domain.launcher.search.normalizedSearchTokens

fun List<InstalledApp>.filterFolderAddCandidates(layout: HomeLayout): List<InstalledApp> =
    filterNot { app -> layout.containsHomeApp(app.identity) }

fun List<InstalledApp>.filterFolderAddCandidates(profileFilter: AppDrawerProfileFilter): List<InstalledApp> =
    filter { app -> profileFilter.matches(app) }

fun List<InstalledApp>.filterFolderAddCandidates(query: String): List<InstalledApp> =
    normalizedSearchTokens(query)
        .takeIf { tokens -> tokens.isNotEmpty() }
        ?.let { tokens ->
            filter { app -> app.matchesFolderAddQuery(tokens) }
        }
        ?: this

fun List<InstalledApp>.folderAddEmptyText(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        isEmpty() -> "No apps left to add"
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL -> "No matching apps"
        else -> "No apps left to add"
    }

fun folderAddResultSummaryText(
    totalCandidateCount: Int,
    resultCount: Int,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL ->
            "${resultCount.appCountLabel()} matching, ${totalCandidateCount.appCountLabel()} left to add"

        else -> "${totalCandidateCount.appCountLabel()} left to add"
    }

fun shouldShowFolderAddClearFilters(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): Boolean = query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL

fun InstalledApp.folderAddCandidateKey(): String =
    "${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}"

private fun InstalledApp.matchesFolderAddQuery(queryTokens: List<String>): Boolean {
    val searchableValues =
        listOf(
            label,
            identity.packageName.value,
            identity.activityName.value,
            identity.profile.id.value,
            identity.profile.type.name,
        ).map { value -> value.lowercase() }

    return searchableValues.containsAllSearchTokens(queryTokens)
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.containsHomeApp

fun List<InstalledApp>.filterFolderAddCandidates(layout: HomeLayout): List<InstalledApp> =
    filterNot { app -> layout.containsHomeApp(app.identity) }

fun List<InstalledApp>.filterFolderAddCandidates(profileFilter: AppDrawerProfileFilter): List<InstalledApp> =
    filter { app -> app.matchesFolderAddProfile(profileFilter) }

fun List<InstalledApp>.filterFolderAddCandidates(query: String): List<InstalledApp> =
    query
        .trim()
        .lowercase()
        .takeIf { trimmedQuery -> trimmedQuery.isNotEmpty() }
        ?.let { trimmedQuery ->
            filter { app -> app.matchesFolderAddQuery(trimmedQuery) }
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

fun InstalledApp.folderAddCandidateKey(): String =
    "${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}"

private fun InstalledApp.matchesFolderAddQuery(query: String): Boolean =
    label.lowercase().contains(query) ||
        identity.packageName.value.lowercase().contains(query) ||
        identity.activityName.value.lowercase().contains(query) ||
        identity.profile.id.value.lowercase().contains(query) ||
        identity.profile.type.name.lowercase().contains(query)

private fun InstalledApp.matchesFolderAddProfile(profileFilter: AppDrawerProfileFilter): Boolean =
    when (profileFilter) {
        AppDrawerProfileFilter.ALL -> true
        AppDrawerProfileFilter.PERSONAL -> identity.profile.type == AppProfileType.PERSONAL
        AppDrawerProfileFilter.WORK -> identity.profile.type == AppProfileType.WORK
        AppDrawerProfileFilter.PRIVATE -> identity.profile.type == AppProfileType.PRIVATE
    }

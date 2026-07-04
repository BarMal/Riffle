package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter

internal enum class AppListSurface {
    DRAWER,
    SEARCH,
}

internal fun appListEmptyText(
    surface: AppListSurface,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() ->
            "No apps matching \"${query.trim()}\"${profileFilter.matchingProfileSuffix()}"

        profileFilter != AppDrawerProfileFilter.ALL -> "No ${profileFilter.label.lowercase()} apps found"
        surface == AppListSurface.DRAWER -> "No launchable apps found"
        else -> "No apps found"
    }

internal fun appListSummaryText(
    totalAppCount: Int,
    resultCount: Int,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL ->
            "${resultCount.appCountLabel()} matching${profileFilter.matchingProfileSuffix()}, " +
                "${totalAppCount.appCountLabel()} total"

        else -> "${totalAppCount.appCountLabel()} available"
    }

internal fun appPanelTitle(
    baseTitle: String,
    resultCount: Int,
    query: String,
    profileFilter: AppDrawerProfileFilter,
): String =
    when {
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL ->
            "$baseTitle (${resultCount.appCountLabel()})"

        else -> baseTitle
    }

internal fun Int.appCountLabel(): String =
    when (this) {
        1 -> "1 app"
        else -> "$this apps"
    }

internal fun AppDrawerProfileFilter.matchingProfileSuffix(): String =
    when (this) {
        AppDrawerProfileFilter.ALL -> ""
        else -> " in ${label.lowercase()}"
    }

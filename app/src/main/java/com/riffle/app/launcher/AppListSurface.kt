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
        query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL -> "No matching apps"
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
            "${resultCount.appCountLabel()} matching, ${totalAppCount.appCountLabel()} total"

        else -> "${totalAppCount.appCountLabel()} available"
    }

internal fun Int.appCountLabel(): String =
    when (this) {
        1 -> "1 app"
        else -> "$this apps"
    }

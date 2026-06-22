package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.containsHomeApp

fun List<InstalledApp>.filterFolderAddCandidates(layout: HomeLayout): List<InstalledApp> =
    filterNot { app -> layout.containsHomeApp(app.identity) }

fun List<InstalledApp>.filterFolderAddCandidates(query: String): List<InstalledApp> =
    query
        .trim()
        .takeIf { trimmedQuery -> trimmedQuery.isNotEmpty() }
        ?.let { trimmedQuery ->
            filter { app ->
                app.label.contains(trimmedQuery, ignoreCase = true) ||
                    app.identity.packageName.value.contains(trimmedQuery, ignoreCase = true) ||
                    app.identity.activityName.value.contains(trimmedQuery, ignoreCase = true)
            }
        }
        ?: this

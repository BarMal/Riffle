package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp

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

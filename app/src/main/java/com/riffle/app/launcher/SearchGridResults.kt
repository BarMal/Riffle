package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp

internal fun searchGridApps(apps: List<InstalledApp>): List<InstalledApp> =
    apps.sortedWith(
        compareBy<InstalledApp> { app -> app.label.lowercase() }
            .thenBy { app -> app.identity.packageName.value }
            .thenBy { app -> app.identity.activityName.value }
            .thenBy { app -> app.identity.profile.id.value },
    )

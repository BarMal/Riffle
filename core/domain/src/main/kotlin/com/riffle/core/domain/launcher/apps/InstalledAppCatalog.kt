package com.riffle.core.domain.launcher.apps

class InstalledAppCatalog {
    fun visibleApps(apps: List<InstalledApp>): List<InstalledApp> =
        apps
            .filter { app -> app.enabled && app.visibility == AppVisibility.VISIBLE }
            .sortedWith(installedAppComparator)

    fun appsForProfile(
        apps: List<InstalledApp>,
        profile: AppProfile,
    ): List<InstalledApp> = visibleApps(apps).filter { app -> app.identity.profile == profile }

    private val installedAppComparator: Comparator<InstalledApp> =
        compareBy<InstalledApp> { app -> app.label.lowercase() }
            .thenBy { app -> app.identity.packageName.value }
            .thenBy { app -> app.identity.activityName.value }
            .thenBy { app -> app.identity.profile.id.value }
}

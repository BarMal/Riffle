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

    fun searchApps(
        apps: List<InstalledApp>,
        query: String,
    ): List<InstalledApp> =
        query.trim().lowercase().let { normalizedQuery ->
            when {
                normalizedQuery.isBlank() -> visibleApps(apps)
                else -> visibleApps(apps).filter { app -> app.matches(normalizedQuery) }
            }
        }

    private fun InstalledApp.matches(query: String): Boolean =
        label.lowercase().contains(query) ||
            identity.packageName.value.lowercase().contains(query) ||
            identity.activityName.value.lowercase().contains(query) ||
            identity.profile.id.value.lowercase().contains(query) ||
            identity.profile.type.name.lowercase().contains(query)

    private val installedAppComparator: Comparator<InstalledApp> =
        compareBy<InstalledApp> { app -> app.label.lowercase() }
            .thenBy { app -> app.identity.packageName.value }
            .thenBy { app -> app.identity.activityName.value }
            .thenBy { app -> app.identity.profile.id.value }
}

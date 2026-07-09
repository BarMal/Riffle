package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppProfileSelection
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.filterByProfile

data class GeneratedLauncherPageSpec(
    val kind: GeneratedLauncherPageKind,
    val defaultPageIdPrefix: String,
    val requiredDataSources: Set<GeneratedLauncherPageDataSource>,
) {
    fun defaultPageId(instance: Int = 1): LauncherPageId {
        require(instance > 0) { "Generated page id instance must be positive." }
        return LauncherPageId("$defaultPageIdPrefix:$instance")
    }

    fun canCreateWith(data: GeneratedLauncherPageData): Boolean =
        when (kind) {
            GeneratedLauncherPageKind.APP -> data.hasVisibleInstalledApps
            GeneratedLauncherPageKind.CATEGORY -> data.hasVisibleInstalledApps && data.appCategoriesAvailable
            GeneratedLauncherPageKind.TODAY -> data.hasVisibleInstalledApps
            GeneratedLauncherPageKind.WORK -> data.hasVisibleAppsFor(AppProfileSelection.work())
            GeneratedLauncherPageKind.PERSONAL -> data.hasVisibleAppsFor(AppProfileSelection.personal())
            GeneratedLauncherPageKind.FAVOURITES -> data.hasVisibleInstalledApps && data.favouriteAppsAvailable
            GeneratedLauncherPageKind.FREQUENTLY_USED -> data.hasVisibleInstalledApps && data.usageStatsAvailable
            GeneratedLauncherPageKind.NOTIFICATION_CARDS -> data.notificationCardsAvailable
        }
}

data class GeneratedLauncherPageData(
    val installedApps: List<InstalledApp> = emptyList(),
    val appCategoriesAvailable: Boolean = false,
    val favouriteAppsAvailable: Boolean = false,
    val usageStatsAvailable: Boolean = false,
    val notificationCardsAvailable: Boolean = false,
) {
    internal val visibleInstalledApps: List<InstalledApp>
        get() = installedApps.filter { app -> app.enabled && app.visibility == AppVisibility.VISIBLE }

    internal val hasVisibleInstalledApps: Boolean
        get() = visibleInstalledApps.isNotEmpty()

    internal fun hasVisibleAppsFor(selection: AppProfileSelection): Boolean {
        return visibleInstalledApps.filterByProfile(selection).isNotEmpty()
    }
}

enum class GeneratedLauncherPageDataSource {
    INSTALLED_APPS,
    APP_CATEGORIES,
    APP_PROFILES,
    FAVOURITE_APPS,
    USAGE_STATS,
    NOTIFICATION_CARDS,
}

val GeneratedLauncherPageKind.spec: GeneratedLauncherPageSpec
    get() =
        when (this) {
            GeneratedLauncherPageKind.APP ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:app",
                    requiredDataSources = setOf(GeneratedLauncherPageDataSource.INSTALLED_APPS),
                )

            GeneratedLauncherPageKind.CATEGORY ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:category",
                    requiredDataSources =
                        setOf(
                            GeneratedLauncherPageDataSource.INSTALLED_APPS,
                            GeneratedLauncherPageDataSource.APP_CATEGORIES,
                        ),
                )

            GeneratedLauncherPageKind.TODAY ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:today",
                    requiredDataSources = setOf(GeneratedLauncherPageDataSource.INSTALLED_APPS),
                )

            GeneratedLauncherPageKind.WORK ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:work",
                    requiredDataSources =
                        setOf(
                            GeneratedLauncherPageDataSource.INSTALLED_APPS,
                            GeneratedLauncherPageDataSource.APP_PROFILES,
                        ),
                )

            GeneratedLauncherPageKind.PERSONAL ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:personal",
                    requiredDataSources =
                        setOf(
                            GeneratedLauncherPageDataSource.INSTALLED_APPS,
                            GeneratedLauncherPageDataSource.APP_PROFILES,
                        ),
                )

            GeneratedLauncherPageKind.FAVOURITES ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:favourites",
                    requiredDataSources =
                        setOf(
                            GeneratedLauncherPageDataSource.INSTALLED_APPS,
                            GeneratedLauncherPageDataSource.FAVOURITE_APPS,
                        ),
                )

            GeneratedLauncherPageKind.FREQUENTLY_USED ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:frequently-used",
                    requiredDataSources =
                        setOf(
                            GeneratedLauncherPageDataSource.INSTALLED_APPS,
                            GeneratedLauncherPageDataSource.USAGE_STATS,
                        ),
                )

            GeneratedLauncherPageKind.NOTIFICATION_CARDS ->
                GeneratedLauncherPageSpec(
                    kind = this,
                    defaultPageIdPrefix = "generated:notification-cards",
                    requiredDataSources = setOf(GeneratedLauncherPageDataSource.NOTIFICATION_CARDS),
                )
        }

object GeneratedLauncherPageSpecs {
    val all: List<GeneratedLauncherPageSpec> =
        GeneratedLauncherPageKind.entries.map { kind -> kind.spec }

    fun forKind(kind: GeneratedLauncherPageKind): GeneratedLauncherPageSpec = kind.spec
}

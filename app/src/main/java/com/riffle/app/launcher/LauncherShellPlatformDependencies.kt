package com.riffle.app.launcher

import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog

data class LauncherShellPlatformDependencies(
    val notificationRepository: LauncherNotificationRepository = LauncherNotificationRepository { emptyList() },
    val widgetProviderRepository: InstalledWidgetProviderRepository = InstalledWidgetProviderRepository { emptyList() },
    val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
    val loadInitialPlatformState: Boolean = false,
    val initialHomeLayoutDeviceClass: HomeLayoutDeviceClass? = null,
    val viewModeAvailability: LauncherViewModeAvailability = defaultLauncherViewModeAvailability(),
    val deleteHostedWidgetId: (HostedWidgetId) -> Unit = {},
    val feedArticleCacheRepository: FeedArticleCacheRepository = NoopFeedArticleCacheRepository,
    /** The workspace store the dock's workspace menu reads (#1351); null leaves the menu unavailable. */
    val workspaceRepository: CachedWorkspaceRepository? = null,
) {
    fun installedWidgetProviders(catalog: WidgetProviderCatalog): List<InstalledWidgetProvider> =
        catalog.sortedProviders(widgetProviderRepository.installedWidgetProviders())
}

fun defaultLauncherViewModeAvailability(): LauncherViewModeAvailability =
    LauncherViewModeAvailability(
        enabledExperimentalModesByDeviceClass =
            HomeLayoutDeviceClass.entries.associateWith {
                setOf(
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.CARD_INTERFACE,
                )
            },
    )

/**
 * What the shipped app offers: only Library, until Cards and Standard are redesigned
 * (#1318-#1325). Stored layouts in a hidden mode resolve to Library on load.
 */
fun libraryOnlyLauncherViewModeAvailability(): LauncherViewModeAvailability =
    LauncherViewModeAvailability(
        enabledExperimentalModesByDeviceClass =
            HomeLayoutDeviceClass.entries.associateWith { setOf(LauncherViewMode.HOME_SCREEN_LIBRARY) },
        alwaysAvailableModes = emptySet(),
        fallbackMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
    )

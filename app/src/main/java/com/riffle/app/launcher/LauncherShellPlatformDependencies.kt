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

/**
 * Every mode on every device class. Since S9 the mode is not a user choice: the stored `LauncherViewMode` is migration
 * input and follows the active workspace (`WorkspaceHome.homeModeFor`), so no stored layout resolves to another mode
 * on load any more (design 3.3, #1324). The Library-only availability the alpha shipped with is retired.
 */
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

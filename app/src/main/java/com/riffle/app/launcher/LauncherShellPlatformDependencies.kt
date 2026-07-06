package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
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
    val viewModeAvailability: LauncherViewModeAvailability = LauncherViewModeAvailability(),
) {
    fun installedWidgetProviders(catalog: WidgetProviderCatalog): List<InstalledWidgetProvider> =
        catalog.sortedProviders(widgetProviderRepository.installedWidgetProviders())
}

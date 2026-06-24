package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog

data class LauncherShellPlatformDependencies(
    val notificationRepository: LauncherNotificationRepository = LauncherNotificationRepository { emptyList() },
    val widgetProviderRepository: InstalledWidgetProviderRepository = InstalledWidgetProviderRepository { emptyList() },
    val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
) {
    fun installedWidgetProviders(catalog: WidgetProviderCatalog): List<InstalledWidgetProvider> =
        catalog.sortedProviders(widgetProviderRepository.installedWidgetProviders())
}

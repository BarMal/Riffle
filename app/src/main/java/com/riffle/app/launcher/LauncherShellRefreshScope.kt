package com.riffle.app.launcher

import kotlinx.coroutines.Job

enum class LauncherShellRefreshScope {
    INSTALLED_APPS,
    NOTIFICATIONS,
    WIDGET_PROVIDERS,
}

fun LauncherShellViewModel.refreshNotifications(): Job {
    return refreshInstalledApps(scope = LauncherShellRefreshScope.NOTIFICATIONS)
}

fun LauncherShellViewModel.refreshWidgetProviders(): Job {
    return refreshInstalledApps(scope = LauncherShellRefreshScope.WIDGET_PROVIDERS)
}

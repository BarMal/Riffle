package com.riffle.app.launcher

import kotlinx.coroutines.Job

enum class LauncherShellRefreshScope {
    INSTALLED_APPS,
    NOTIFICATIONS,
}

fun LauncherShellViewModel.refreshNotifications(): Job {
    return refreshInstalledApps(scope = LauncherShellRefreshScope.NOTIFICATIONS)
}

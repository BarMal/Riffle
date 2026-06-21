package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName

class AppNotificationCounter {
    fun countByPackage(notifications: List<LauncherNotification>): Map<AppPackageName, Int> =
        notifications
            .groupingBy { notification -> notification.packageName }
            .eachCount()
            .filterValues { count -> count > 0 }
}

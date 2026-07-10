package com.riffle.core.domain.launcher.notifications

class AppNotificationCounter {
    fun countByCategory(notifications: List<LauncherNotification>): Map<NotificationCategory, Int> =
        notifications
            .groupingBy { notification -> notification.category }
            .eachCount()
            .filterValues { count -> count > 0 }
}

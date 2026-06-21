package com.riffle.app.launcher.notifications

import android.app.Notification
import com.riffle.core.domain.launcher.notifications.NotificationPriority

@Suppress("DEPRECATION")
fun Int.toLauncherNotificationPriority(): NotificationPriority =
    when (this) {
        Notification.PRIORITY_MIN -> NotificationPriority.MIN
        Notification.PRIORITY_LOW -> NotificationPriority.LOW
        Notification.PRIORITY_DEFAULT -> NotificationPriority.DEFAULT
        Notification.PRIORITY_HIGH -> NotificationPriority.HIGH
        Notification.PRIORITY_MAX -> NotificationPriority.MAX
        else -> NotificationPriority.UNKNOWN
    }

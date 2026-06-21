package com.riffle.app.launcher.notifications

import android.app.Notification
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationCategory.UNKNOWN

fun String?.toLauncherNotificationCategory(): NotificationCategory = categoryMap[this] ?: UNKNOWN

private val categoryMap: Map<String, NotificationCategory> =
    mapOf(
        Notification.CATEGORY_MESSAGE to NotificationCategory.MESSAGE,
        Notification.CATEGORY_EMAIL to NotificationCategory.EMAIL,
        Notification.CATEGORY_CALL to NotificationCategory.CALL,
        Notification.CATEGORY_MISSED_CALL to NotificationCategory.MISSED_CALL,
        Notification.CATEGORY_ALARM to NotificationCategory.ALARM,
        Notification.CATEGORY_EVENT to NotificationCategory.EVENT,
        Notification.CATEGORY_REMINDER to NotificationCategory.REMINDER,
        Notification.CATEGORY_TRANSPORT to NotificationCategory.TRANSPORT,
        Notification.CATEGORY_NAVIGATION to NotificationCategory.NAVIGATION,
        Notification.CATEGORY_LOCATION_SHARING to NotificationCategory.LOCATION,
        Notification.CATEGORY_SOCIAL to NotificationCategory.SOCIAL,
        Notification.CATEGORY_PROMO to NotificationCategory.PROMOTION,
        Notification.CATEGORY_RECOMMENDATION to NotificationCategory.RECOMMENDATION,
        Notification.CATEGORY_STATUS to NotificationCategory.STATUS,
        Notification.CATEGORY_SYSTEM to NotificationCategory.SYSTEM,
        Notification.CATEGORY_SERVICE to NotificationCategory.SERVICE,
        Notification.CATEGORY_PROGRESS to NotificationCategory.PROGRESS,
        Notification.CATEGORY_ERROR to NotificationCategory.ERROR,
        Notification.CATEGORY_STOPWATCH to NotificationCategory.STOPWATCH,
        Notification.CATEGORY_WORKOUT to NotificationCategory.WORKOUT,
    )

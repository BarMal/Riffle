package com.riffle.app.launcher.notifications

import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

fun StatusBarNotification.toLauncherNotification(): LauncherNotification =
    LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = AppPackageName(packageName),
        category = notification.category.toLauncherNotificationCategory(),
        postedAtEpochMillis = postTime,
    )

package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.json.JSONArray
import org.json.JSONObject

fun encodeActiveNotifications(notifications: List<LauncherNotification>): String =
    JSONArray(notifications.map(::encodeNotification)).toString()

fun decodeActiveNotifications(value: String): List<LauncherNotification> =
    JSONArray(value).let { json ->
        (0 until json.length())
            .map { index -> json.getJSONObject(index) }
            .map { notification -> notification.toNotification() }
    }

private fun encodeNotification(notification: LauncherNotification): JSONObject =
    JSONObject()
        .put("key", notification.key.value)
        .put("packageName", notification.packageName.value)
        .put("category", notification.category.name)
        .put("postedAtEpochMillis", notification.postedAtEpochMillis)

private fun JSONObject.toNotification(): LauncherNotification =
    LauncherNotification(
        key = LauncherNotificationKey(getString("key")),
        packageName = AppPackageName(getString("packageName")),
        category = optNotificationCategory(),
        postedAtEpochMillis = optLong("postedAtEpochMillis", 0L),
    )

private fun JSONObject.optNotificationCategory(): NotificationCategory =
    optString("category")
        .takeIf(String::isNotBlank)
        ?.let { category -> runCatching { NotificationCategory.valueOf(category) }.getOrNull() }
        ?: NotificationCategory.UNKNOWN

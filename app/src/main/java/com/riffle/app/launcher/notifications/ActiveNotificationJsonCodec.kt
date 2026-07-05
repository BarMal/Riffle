package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import org.json.JSONArray
import org.json.JSONObject

fun encodeActiveNotifications(notifications: List<LauncherNotification>): String =
    JSONArray(notifications.map(::encodeNotification)).toString()

fun decodeActiveNotifications(value: String): List<LauncherNotification> =
    JSONArray(value).let { json ->
        (0 until json.length())
            .mapNotNull { index -> json.optJSONObject(index)?.toNotificationOrNull() }
    }

private fun encodeNotification(notification: LauncherNotification): JSONObject =
    JSONObject()
        .put("key", notification.key.value)
        .put("packageName", notification.packageName.value)
        .put("profileId", notification.profileId.value)
        .put("category", notification.category.name)
        .put("priority", notification.priority.name)
        .put("canDismiss", notification.canDismiss)
        .put("postedAtEpochMillis", notification.postedAtEpochMillis)

private fun JSONObject.toNotificationOrNull(): LauncherNotification? =
    runCatching {
        LauncherNotification(
            key = LauncherNotificationKey(getString("key")),
            packageName = AppPackageName(getString("packageName")),
            profileId = optProfileId(),
            category = optNotificationCategory(),
            priority = optNotificationPriority(),
            canDismiss = optBoolean("canDismiss", false),
            postedAtEpochMillis = optLong("postedAtEpochMillis", 0L),
        )
    }.getOrNull()

private fun JSONObject.optProfileId(): AppProfileId =
    optString("profileId")
        .takeIf(String::isNotBlank)
        ?.let(::AppProfileId)
        ?: AppProfile.personal().id

private fun JSONObject.optNotificationCategory(): NotificationCategory =
    optString("category")
        .takeIf(String::isNotBlank)
        ?.let { category -> runCatching { NotificationCategory.valueOf(category) }.getOrNull() }
        ?: NotificationCategory.UNKNOWN

private fun JSONObject.optNotificationPriority(): NotificationPriority =
    optString("priority")
        .takeIf(String::isNotBlank)
        ?.let { priority -> runCatching { NotificationPriority.valueOf(priority) }.getOrNull() }
        ?: NotificationPriority.UNKNOWN

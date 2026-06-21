package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId

data class LauncherNotification(
    val key: LauncherNotificationKey,
    val packageName: AppPackageName,
    val profileId: AppProfileId = AppProfile.personal().id,
    val category: NotificationCategory = NotificationCategory.UNKNOWN,
    val canDismiss: Boolean = false,
    val postedAtEpochMillis: Long,
)

@JvmInline
value class LauncherNotificationKey(val value: String)

enum class NotificationCategory {
    UNKNOWN,
    MESSAGE,
    EMAIL,
    CALL,
    MISSED_CALL,
    ALARM,
    EVENT,
    REMINDER,
    TRANSPORT,
    NAVIGATION,
    LOCATION,
    SOCIAL,
    PROMOTION,
    RECOMMENDATION,
    STATUS,
    SYSTEM,
    SERVICE,
    PROGRESS,
    ERROR,
    STOPWATCH,
    WORKOUT,
}

fun interface LauncherNotificationRepository {
    fun activeNotifications(): List<LauncherNotification>
}

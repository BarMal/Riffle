package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId

data class LauncherNotification(
    val key: LauncherNotificationKey,
    val packageName: AppPackageName,
    val profileId: AppProfileId = AppProfile.personal().id,
    val category: NotificationCategory = NotificationCategory.UNKNOWN,
    val priority: NotificationPriority = NotificationPriority.UNKNOWN,
    val canDismiss: Boolean = false,
    /** True only when the platform notification exposes a media session. */
    val isMediaSession: Boolean = false,
    val title: String = "",
    val text: String = "",
    val largeIconPngBase64: String? = null,
    /**
     * Whether the posting app published a person icon (a contact photo) and a picture (big-picture style). The images
     * themselves are never part of this value: they are held in memory by the platform listener for as long as the
     * notification is active (see `NotificationImageStore`), so only these two flags are stored with it.
     */
    val hasPersonIcon: Boolean = false,
    val hasPicture: Boolean = false,
    /** Individual messages when the source notification carries per-message history, newest last. */
    val messages: List<LauncherNotificationMessage> = emptyList(),
    val postedAtEpochMillis: Long,
)

data class LauncherNotificationMessage(
    val sender: String,
    val text: String,
    val timestampEpochMillis: Long,
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

enum class NotificationPriority(val rank: Int) {
    UNKNOWN(rank = 0),
    MIN(rank = 1),
    LOW(rank = 2),
    DEFAULT(rank = 3),
    HIGH(rank = 4),
    MAX(rank = 5),
}

fun interface LauncherNotificationRepository {
    fun activeNotifications(): List<LauncherNotification>
}

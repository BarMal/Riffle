package com.riffle.app.launcher.notifications

import android.content.pm.LauncherApps
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

class RiffleNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { DataStoreActiveNotificationRepository(this) }
    private val notificationMapper by lazy {
        StatusBarNotificationMapper(
            userManager = getSystemService(UserManager::class.java),
            launcherApps = getSystemService(LauncherApps::class.java),
        )
    }

    override fun onListenerConnected() {
        RiffleNotificationListenerConnection.connect(this)
        saveActiveNotifications()
    }

    override fun onListenerDisconnected() {
        RiffleNotificationListenerConnection.disconnect(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        saveActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        saveActiveNotifications()
    }

    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        runCatching {
            keys.forEach { key -> cancelNotification(key.value) }
            saveActiveNotifications()
        }.isSuccess

    private fun saveActiveNotifications() {
        activeNotificationSnapshotOrNull(
            activeNotifications = { activeNotifications },
            mapper = notificationMapper::map,
        )?.let(repository::saveActiveNotifications)
    }
}

/**
 * A notification listener may be disconnected while its platform snapshot is read. Keep the
 * persisted snapshot in that case: replacing it with an empty list would hide notifications, and
 * allowing the platform exception out of the service would crash the launcher process.
 */
internal fun <Input, Output> activeNotificationSnapshotOrNull(
    activeNotifications: () -> Array<Input>?,
    mapper: (Input) -> Output,
): List<Output>? =
    runCatching {
        activeNotifications()
            ?.map(mapper)
            .orEmpty()
    }.getOrNull()

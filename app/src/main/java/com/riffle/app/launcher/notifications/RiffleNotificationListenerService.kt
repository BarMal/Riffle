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
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.connect(this)
            saveActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.disconnect(this)
            AndroidNotificationStageActionGateway.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        ignoreNotificationListenerFailure {
            sbn?.let { notification -> AndroidNotificationStageActionGateway.replace(this, notification) }
            saveActiveNotifications()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        ignoreNotificationListenerFailure {
            sbn?.key?.let(AndroidNotificationStageActionGateway::remove)
            saveActiveNotifications()
        }
    }

    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        runCatching {
            keys.forEach { key -> cancelNotification(key.value) }
            saveActiveNotifications()
        }.isSuccess

    private fun saveActiveNotifications() {
        ignoreNotificationListenerFailure {
            activeNotifications
                ?.also { notifications -> AndroidNotificationStageActionGateway.replaceAll(this, notifications) }
                ?.map(notificationMapper::map)
                ?.let(repository::saveActiveNotifications)
        }
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

/**
 * Notification access can connect the listener while its backing storage is unavailable. Listener
 * callbacks run in the launcher process, so persistence failures must not escape and crash it.
 */
internal fun <Input, Output> saveActiveNotificationSnapshot(
    activeNotifications: () -> Array<Input>?,
    mapper: (Input) -> Output,
    saveNotifications: (List<Output>) -> Unit,
) {
    activeNotificationSnapshotOrNull(
        activeNotifications = activeNotifications,
        mapper = mapper,
    )?.let { snapshot -> runCatching { saveNotifications(snapshot) } }
}

/**
 * Listener connection callbacks can race with permission changes and lazy platform service setup.
 * Keep every callback boundary from propagating a transient platform failure into the launcher process.
 */
internal fun ignoreNotificationListenerFailure(action: () -> Unit) {
    runCatching(action)
}

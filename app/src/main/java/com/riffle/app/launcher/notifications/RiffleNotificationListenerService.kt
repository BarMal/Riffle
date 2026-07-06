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
        repository.saveActiveNotifications(
            activeNotifications
                ?.map(notificationMapper::map)
                .orEmpty(),
        )
    }
}

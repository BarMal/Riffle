package com.riffle.app.launcher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

class RiffleNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { SharedPreferencesActiveNotificationRepository(this) }

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
                ?.map { notification -> notification.toLauncherNotification() }
                .orEmpty(),
        )
    }
}

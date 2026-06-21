package com.riffle.app.launcher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class RiffleNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { SharedPreferencesActiveNotificationRepository(this) }

    override fun onListenerConnected() {
        saveActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        saveActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        saveActiveNotifications()
    }

    private fun saveActiveNotifications() {
        repository.saveActiveNotifications(
            activeNotifications
                ?.map { notification -> notification.toLauncherNotification() }
                .orEmpty(),
        )
    }
}

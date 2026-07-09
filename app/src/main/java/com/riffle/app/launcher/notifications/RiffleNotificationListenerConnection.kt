package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

object RiffleNotificationListenerConnection {
    private var listener: RiffleNotificationListenerService? = null

    fun isConnected(): Boolean = listener != null

    fun connect(listener: RiffleNotificationListenerService) {
        this.listener = listener
    }

    fun disconnect(listener: RiffleNotificationListenerService) {
        if (this.listener == listener) {
            this.listener = null
        }
    }

    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        listener
            ?.dismissNotifications(keys)
            ?: false
}

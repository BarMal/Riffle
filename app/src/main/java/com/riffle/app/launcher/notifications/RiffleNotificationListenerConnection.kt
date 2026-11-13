package com.riffle.app.launcher.notifications

import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

object RiffleNotificationListenerConnection {
    private var listener: RiffleNotificationListenerService? = null
    private val observers = mutableSetOf<() -> Unit>()

    fun isConnected(): Boolean = listener != null

    fun connect(listener: RiffleNotificationListenerService) {
        this.listener = listener
        notifyObservers()
    }

    fun disconnect(listener: RiffleNotificationListenerService) {
        if (this.listener == listener) {
            this.listener = null
            notifyObservers()
        }
    }

    fun observeConnection(onChanged: () -> Unit): () -> Unit {
        observers += onChanged
        return { observers -= onChanged }
    }

    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        listener
            ?.dismissNotifications(keys)
            ?: false

    /** Snoozes the notification for [durationMillis]; false when the listener is not connected or refused. */
    fun snoozeNotification(
        key: LauncherNotificationKey,
        durationMillis: Long,
    ): Boolean = listener?.snooze(key, durationMillis) ?: false

    /**
     * The platform's active notifications, read straight from the connected listener for the diagnostics page;
     * null when the listener is not connected or the read fails.
     */
    fun activeNotifications(): List<StatusBarNotification>? =
        listener?.let { connected -> runCatching { connected.activeNotifications?.toList() }.getOrNull() }

    private fun notifyObservers() {
        observers.toList().forEach { observer -> observer() }
    }
}

package com.riffle.app.launcher.notifications

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

    private fun notifyObservers() {
        observers.toList().forEach { observer -> observer() }
    }
}

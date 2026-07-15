package com.riffle.app.launcher.notifications

fun interface ActiveNotificationChangeSource {
    fun observeActiveNotifications(onChanged: () -> Unit): () -> Unit
}

fun interface NotificationListenerConnectionChangeSource {
    fun observeConnection(onChanged: () -> Unit): () -> Unit
}

class ActiveNotificationRefreshCoordinator(
    private val notificationChangeSource: ActiveNotificationChangeSource,
    private val connectionChangeSource: NotificationListenerConnectionChangeSource =
        NotificationListenerConnectionChangeSource(RiffleNotificationListenerConnection::observeConnection),
    private val dispatchOnMainThread: (() -> Unit) -> Unit,
    private val refreshNotifications: () -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
) {
    private var removeNotificationObserver: (() -> Unit)? = null
    private var removeConnectionObserver: (() -> Unit)? = null

    fun start() {
        if (removeNotificationObserver != null) return
        removeNotificationObserver = notificationChangeSource.observeActiveNotifications {
            dispatchRefresh()
        }
        removeConnectionObserver = connectionChangeSource.observeConnection(::dispatchRefresh)
    }

    fun stop() {
        removeNotificationObserver?.invoke()
        removeNotificationObserver = null
        removeConnectionObserver?.invoke()
        removeConnectionObserver = null
    }

    private fun dispatchRefresh() = dispatchOnMainThread {
        refreshPlatformStatuses()
        refreshNotifications()
    }
}

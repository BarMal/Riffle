package com.riffle.app.launcher.notifications

fun interface ActiveNotificationChangeSource {
    fun observeActiveNotifications(onChanged: () -> Unit)
}

class ActiveNotificationRefreshCoordinator(
    private val notificationChangeSource: ActiveNotificationChangeSource,
    private val dispatchOnMainThread: (() -> Unit) -> Unit,
    private val refreshNotifications: () -> Unit,
) {
    fun start() {
        notificationChangeSource.observeActiveNotifications {
            dispatchOnMainThread(refreshNotifications)
        }
    }
}

package com.riffle.app.launcher.notifications

fun interface ActiveNotificationChangeSource {
    fun observeActiveNotifications(onChanged: () -> Unit)
}

class ActiveNotificationRefreshCoordinator(
    private val notificationChangeSource: ActiveNotificationChangeSource,
    private val dispatchOnMainThread: (() -> Unit) -> Unit,
    private val refreshNotifications: () -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
) {
    fun start() {
        notificationChangeSource.observeActiveNotifications {
            dispatchOnMainThread {
                refreshPlatformStatuses()
                refreshNotifications()
            }
        }
    }
}

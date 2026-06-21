package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

fun interface NotificationDismissalGateway {
    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean
}

object AndroidNotificationDismissalGateway : NotificationDismissalGateway {
    override fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        RiffleNotificationListenerConnection.dismissNotifications(keys)
}

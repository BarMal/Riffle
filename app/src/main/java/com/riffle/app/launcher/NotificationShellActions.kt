package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationDismissalGateway

internal fun interface LauncherNotificationActionHandler {
    fun handle(action: LauncherShellAction): Boolean
}

internal class DefaultLauncherNotificationActionHandler(
    private val notificationDismissalGateway: NotificationDismissalGateway,
    private val refreshNotifications: () -> Unit,
) : LauncherNotificationActionHandler {
    override fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherNotificationActionRoute()) {
            is LauncherNotificationActionRoute.DismissNotifications -> {
                if (notificationDismissalGateway.dismissNotifications(route.action.keys)) {
                    refreshNotifications()
                }
                true
            }

            null -> false
        }
}

internal sealed interface LauncherNotificationActionRoute {
    data class DismissNotifications(
        val action: LauncherShellAction.DismissNotifications,
    ) : LauncherNotificationActionRoute
}

internal fun LauncherShellAction.launcherNotificationActionRoute(): LauncherNotificationActionRoute? =
    when (this) {
        is LauncherShellAction.DismissNotifications -> LauncherNotificationActionRoute.DismissNotifications(this)
        else -> null
    }

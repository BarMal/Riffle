package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationDismissalGateway

fun LauncherShellAction.handleNotificationAction(
    viewModel: LauncherShellViewModel,
    notificationDismissalGateway: NotificationDismissalGateway,
): Boolean =
    when (this) {
        is LauncherShellAction.DismissNotifications -> {
            if (notificationDismissalGateway.dismissNotifications(keys)) {
                viewModel.refreshNotifications()
            }
            true
        }

        else -> false
    }

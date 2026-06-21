package com.riffle.app.launcher

import android.content.Intent
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway

fun LauncherShellAction.handleSettingsAction(
    viewModel: LauncherShellViewModel,
    notificationAccessGateway: AndroidNotificationAccessGateway,
    openIntent: (Intent) -> Unit,
): Boolean =
    when (this) {
        is LauncherShellAction.SelectWallpaperSource -> {
            viewModel.onWallpaperSourceSelected(this)
            true
        }

        LauncherShellAction.RequestNotificationAccess -> {
            runCatching {
                openIntent(notificationAccessGateway.createNotificationListenerSettingsIntent())
            }
            true
        }

        else -> false
    }

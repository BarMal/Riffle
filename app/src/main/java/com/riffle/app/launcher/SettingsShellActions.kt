package com.riffle.app.launcher

import android.content.Intent
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway

fun LauncherShellAction.handleSettingsAction(
    viewModel: LauncherShellViewModel,
    notificationAccessGateway: AndroidNotificationAccessGateway,
    openIntent: (Intent) -> Unit,
    exportBackup: () -> Unit,
    importBackup: () -> Unit,
): Boolean =
    when (this) {
        is LauncherShellAction.SelectWallpaperSource -> {
            viewModel.onLauncherSettingsActionSelected(this)
            true
        }

        is LauncherShellAction.SelectHomeSwipeGestureAction -> {
            viewModel.onLauncherSettingsActionSelected(this)
            true
        }

        LauncherShellAction.ResetHomeSwipeGestureActions -> {
            viewModel.onLauncherSettingsActionSelected(this)
            true
        }

        is LauncherShellAction.SelectHapticFeedbackStrength -> {
            viewModel.onLauncherSettingsActionSelected(this)
            true
        }

        LauncherShellAction.RequestNotificationAccess -> {
            runCatching {
                openIntent(notificationAccessGateway.createNotificationListenerSettingsIntent())
            }
            true
        }

        LauncherShellAction.ExportLauncherBackup -> {
            exportBackup()
            true
        }

        LauncherShellAction.RequestImportLauncherBackup -> {
            importBackup()
            true
        }

        else -> false
    }

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun SettingsPermissionsSection(
    notificationAccessStatus: NotificationAccessStatus,
    overlayDockPermissionStatus: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Permissions") {
        NotificationAccessSetting(
            status = notificationAccessStatus,
            onAction = onAction,
        )
        OverlayDockPermissionSetting(
            status = overlayDockPermissionStatus,
            onAction = onAction,
        )
    }
}

@Composable
private fun NotificationAccessSetting(
    status: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsClickableRow(
        title = "Notifications",
        subtitle = status.settingsNotificationAccessLabel(),
        onClick = { onAction(LauncherShellAction.RequestNotificationAccess) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAction(LauncherShellAction.OpenNotifications) }) {
                    SettingsButtonText(text = "View")
                }
                TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                    SettingsButtonText(text = "Open")
                }
            }
        },
    )
}

@Composable
private fun OverlayDockPermissionSetting(
    status: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsClickableRow(
        title = "Floating dock",
        subtitle = status.settingsOverlayDockPermissionLabel(),
        onClick = { onAction(LauncherShellAction.RequestOverlayDockPermission) },
        trailingContent = {
            SettingsButtonText(text = "Open")
        },
    )
}

internal fun NotificationAccessStatus.settingsNotificationAccessLabel(): String =
    when (this) {
        NotificationAccessStatus.UNKNOWN -> "Unknown"
        NotificationAccessStatus.GRANTED -> "Allowed"
        NotificationAccessStatus.NOT_GRANTED -> "Not allowed"
    }

internal fun OverlayDockPermissionStatus.settingsOverlayDockPermissionLabel(): String =
    when (this) {
        OverlayDockPermissionStatus.UNKNOWN -> "Unknown"
        OverlayDockPermissionStatus.GRANTED -> "Allowed"
        OverlayDockPermissionStatus.NOT_GRANTED -> "Not allowed"
    }

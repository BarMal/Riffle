package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Notifications",
            subtitle = status.settingsNotificationAccessLabel(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAction(LauncherShellAction.OpenNotifications) }) {
                SettingsButtonText(text = "View")
            }
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                SettingsButtonText(text = "Open")
            }
        }
    }
}

@Composable
private fun OverlayDockPermissionSetting(
    status: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Floating dock",
            subtitle = status.settingsOverlayDockPermissionLabel(),
        )
        TextButton(onClick = { onAction(LauncherShellAction.RequestOverlayDockPermission) }) {
            SettingsButtonText(text = "Open")
        }
    }
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

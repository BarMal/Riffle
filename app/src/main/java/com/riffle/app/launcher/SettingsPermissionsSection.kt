package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun SettingsPermissionsSection(
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    notificationAccessStatus: NotificationAccessStatus,
    overlayDockPermissionStatus: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Permissions") {
        SettingsHomeAppSetting(
            status = homeRoleStatus,
            firstRunStatus = firstRunStatus,
            onAction = onAction,
        )
        permissionSetting(
            title = "Notification access",
            status = notificationAccessStatus.permissionSettingsLabel(),
            actionLabel = notificationAccessStatus.permissionActionLabel("Allow notification access"),
            onAction = { onAction(LauncherShellAction.RequestNotificationAccess) },
        )
        permissionSetting(
            title = "Floating dock access",
            status = overlayDockPermissionStatus.permissionSettingsLabel(),
            actionLabel = overlayDockPermissionStatus.permissionActionLabel("Allow overlay access"),
            onAction = { onAction(LauncherShellAction.RequestOverlayDockPermission) },
        )
    }
}

@Composable
private fun permissionSetting(
    title: String,
    status: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    if (actionLabel == null) {
        SettingsListRow(title = title, subtitle = status, trailingContent = { SettingsButtonText(text = "Allowed") })
    } else {
        SettingsClickableRow(
            title = title,
            subtitle = status,
            onClick = onAction,
            trailingContent = { SettingsButtonText(text = actionLabel) },
        )
    }
}

private fun NotificationAccessStatus.permissionSettingsLabel(): String =
    when (this) {
        NotificationAccessStatus.GRANTED -> "Notification listener is connected."
        NotificationAccessStatus.NOT_GRANTED -> "Allow access to show notification cards and app stages."
        NotificationAccessStatus.REVOKED -> "Access was revoked. Restore it to show notification cards and app stages."
        NotificationAccessStatus.UNKNOWN -> "Checking notification access."
    }

private fun NotificationAccessStatus.permissionActionLabel(label: String): String? =
    takeUnless { this == NotificationAccessStatus.GRANTED }?.let { label }

private fun OverlayDockPermissionStatus.permissionSettingsLabel(): String =
    when (this) {
        OverlayDockPermissionStatus.GRANTED -> "Floating dock can appear above other apps."
        OverlayDockPermissionStatus.NOT_GRANTED -> "Allow access to use the Floating dock above other apps."
        OverlayDockPermissionStatus.UNKNOWN -> "Checking Floating dock access."
    }

private fun OverlayDockPermissionStatus.permissionActionLabel(label: String): String? =
    takeUnless { this == OverlayDockPermissionStatus.GRANTED }?.let { label }

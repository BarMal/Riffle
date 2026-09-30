package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

@Composable
internal fun SettingsPermissionsSection(
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    notificationAccessStatus: NotificationAccessStatus,
    overlayDockPermissionStatus: OverlayDockPermissionStatus,
    calendarAccessStatus: CalendarAccessStatus = CalendarAccessStatus.UNKNOWN,
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
        permissionSetting(
            title = "Calendar",
            status = calendarAccessStatus.calendarAccessSettingsLabel(),
            actionLabel = calendarAccessStatus.calendarAccessActionLabel(),
            onAction = { onAction(LauncherShellAction.RequestCalendarAccess) },
        )
    }
}

/** The rationale lives here so the user always reads why before the system dialog can appear. */
internal fun CalendarAccessStatus.calendarAccessSettingsLabel(): String =
    when (this) {
        CalendarAccessStatus.GRANTED ->
            "Your next events can appear on cards. Private events stay hidden. Nothing is stored or sent."
        CalendarAccessStatus.NOT_GRANTED ->
            "Optional. Allow access to show your next events on cards. Riffle reads event times, titles " +
                "and places on this device only, never stores or sends them, and asks only when you tap Allow."
        CalendarAccessStatus.DENIED_PERMANENTLY ->
            "Calendar access is turned off. Open Android settings to allow it. Riffle works fine without it."
        CalendarAccessStatus.UNKNOWN -> "Checking calendar access."
    }

internal fun CalendarAccessStatus.calendarAccessActionLabel(): String? =
    when (this) {
        CalendarAccessStatus.GRANTED -> null
        CalendarAccessStatus.DENIED_PERMANENTLY -> "Open app settings"
        CalendarAccessStatus.NOT_GRANTED,
        CalendarAccessStatus.UNKNOWN,
        -> "Allow calendar access"
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

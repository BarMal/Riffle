package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

/** Privacy and access > Access: each permission the launcher can use, with its state and a way to change it. */
@Composable
internal fun SettingsPermissionsSection(
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    notificationAccessStatus: NotificationAccessStatus,
    overlayDockPermissionStatus: OverlayDockPermissionStatus,
    calendarAccessStatus: CalendarAccessStatus = CalendarAccessStatus.UNKNOWN,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_access)) {
        SettingsHomeAppSetting(
            modifier = Modifier.settingAnchor(SettingKey.ACCESS_DEFAULT_HOME),
            status = homeRoleStatus,
            firstRunStatus = firstRunStatus,
            onAction = onAction,
        )
        permissionSetting(
            modifier = Modifier.settingAnchor(SettingKey.ACCESS_NOTIFICATIONS),
            title = SettingKey.ACCESS_NOTIFICATIONS.label(),
            status = notificationAccessStatus.permissionSettingsLabel(),
            actionLabel = notificationAccessStatus.permissionActionLabel("Allow notification access"),
            onAction = { onAction(LauncherShellAction.RequestNotificationAccess) },
        )
        UsageAccessSetting()
        permissionSetting(
            modifier = Modifier.settingAnchor(SettingKey.ACCESS_CALENDAR),
            title = SettingKey.ACCESS_CALENDAR.label(),
            status = calendarAccessStatus.calendarAccessSettingsLabel(),
            actionLabel = calendarAccessStatus.calendarAccessActionLabel(),
            onAction = { onAction(LauncherShellAction.RequestCalendarAccess) },
        )
        permissionSetting(
            modifier = Modifier.settingAnchor(SettingKey.ACCESS_FLOATING_DOCK),
            title = SettingKey.ACCESS_FLOATING_DOCK.label(),
            status = overlayDockPermissionStatus.permissionSettingsLabel(),
            actionLabel = overlayDockPermissionStatus.permissionActionLabel("Allow overlay access"),
            onAction = { onAction(LauncherShellAction.RequestOverlayDockPermission) },
        )
    }
}

/**
 * Usage access, which Recents reads: its state from the Recents source and Android's own settings screen as the way to
 * change it. Without the sources host (tests, screenshots) only its name and a note show.
 */
@Composable
private fun UsageAccessSetting() {
    val host = LocalWorkspaceSettingsHost.current
    val controller = host?.sources
    var row by remember { mutableStateOf<SourceRow?>(null) }
    if (controller != null) {
        DisposableEffect(controller) {
            controller.open()
            onDispose { controller.close() }
        }
        val statuses by controller.statuses.collectAsState()
        val disabled by controller.disabled.collectAsState()
        row =
            remember(controller, statuses, disabled) {
                controller.rows(statuses, disabled).firstOrNull { it.id == SourceIds.RECENT_APPS }
            }
    }
    val granted = row?.status?.let { it != SourceStatus.NEEDS_PERMISSION }
    permissionSetting(
        modifier = Modifier.settingAnchor(SettingKey.ACCESS_USAGE),
        title = SettingKey.ACCESS_USAGE.label(),
        status =
            when (granted) {
                true -> stringResource(R.string.settings_access_usage_granted)
                false -> stringResource(R.string.settings_access_usage_not_granted)
                null -> stringResource(R.string.settings_access_usage_unknown)
            },
        actionLabel = stringResource(R.string.settings_access_usage_action).takeIf { granted == false },
        onAction = { host?.onRequestSourceAccess?.invoke(SourceIds.RECENT_APPS) },
    )
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
    modifier: Modifier = Modifier,
    title: String,
    status: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    if (actionLabel == null) {
        SettingsListRow(
            modifier = modifier,
            title = title,
            subtitle = status,
            trailingContent = { SettingsButtonText(text = "Allowed") },
        )
    } else {
        SettingsClickableRow(
            modifier = modifier,
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

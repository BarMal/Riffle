package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory

@Composable
fun NotificationOverviewSurface(
    groups: List<AppNotificationGroup>,
    apps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Notifications",
        onAction = onAction,
    ) {
        if (groups.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = groups,
                    key = { group -> "${group.profileId.value}:${group.packageName.value}" },
                ) { group ->
                    NotificationGroupRow(
                        group = group,
                        app = apps.firstOrNull { app -> app.matches(group) },
                        appIconLoader = appIconLoader,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No active notifications",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun NotificationGroupRow(
    group: AppNotificationGroup,
    app: InstalledApp?,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(enabled = app != null) {
                    app?.let { matchedApp -> onAction(LauncherShellAction.LaunchApp(matchedApp.identity)) }
                }
                .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            LauncherAppIcon(
                identity = app.identity,
                label = app.label,
                iconLoader = appIconLoader,
                modifier = Modifier.launcherIconSize(),
                shape = CircleShape,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = app?.label ?: group.packageName.value,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${group.packageName.value} - ${group.latestCategory.label} - ${group.clearableLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = group.latestAgeBucket.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NotificationCountBadge(count = group.count)
    }
}

private fun InstalledApp.matches(group: AppNotificationGroup): Boolean =
    identity.packageName == group.packageName && identity.profile.id == group.profileId

private val NotificationAgeBucket.label: String
    get() =
        when (this) {
            NotificationAgeBucket.NOW -> "Now"
            NotificationAgeBucket.RECENT -> "Recent"
            NotificationAgeBucket.TODAY -> "Today"
            NotificationAgeBucket.OLDER -> "Older"
        }

private val NotificationCategory.label: String
    get() =
        when (this) {
            NotificationCategory.UNKNOWN -> "Unknown"
            NotificationCategory.MESSAGE -> "Message"
            NotificationCategory.EMAIL -> "Email"
            NotificationCategory.CALL -> "Call"
            NotificationCategory.MISSED_CALL -> "Missed call"
            NotificationCategory.ALARM -> "Alarm"
            NotificationCategory.EVENT -> "Event"
            NotificationCategory.REMINDER -> "Reminder"
            NotificationCategory.TRANSPORT -> "Transport"
            NotificationCategory.NAVIGATION -> "Navigation"
            NotificationCategory.LOCATION -> "Location"
            NotificationCategory.SOCIAL -> "Social"
            NotificationCategory.PROMOTION -> "Promotion"
            NotificationCategory.RECOMMENDATION -> "Recommendation"
            NotificationCategory.STATUS -> "Status"
            NotificationCategory.SYSTEM -> "System"
            NotificationCategory.SERVICE -> "Service"
            NotificationCategory.PROGRESS -> "Progress"
            NotificationCategory.ERROR -> "Error"
            NotificationCategory.STOPWATCH -> "Stopwatch"
            NotificationCategory.WORKOUT -> "Workout"
        }

private val AppNotificationGroup.clearableLabel: String
    get() =
        clearableCount.let { clearable ->
            when {
                clearable == 0 -> "Pinned"
                clearable == count -> "Clearable $count/$count"
                else -> "Clearable $clearable/$count"
            }
        }

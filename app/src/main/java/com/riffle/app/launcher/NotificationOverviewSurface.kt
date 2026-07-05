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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority

@Composable
fun NotificationOverviewSurface(
    groups: List<AppNotificationGroup>,
    categoryCounts: Map<NotificationCategory, Int>,
    apps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    title: String = "Notifications",
) {
    LauncherPanel(
        title = notificationOverviewTitle(baseTitle = title, groups = groups),
        onAction = onAction,
    ) {
        if (groups.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    NotificationCategorySummary(categoryCounts = categoryCounts)
                }
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

internal fun notificationOverviewTitle(
    baseTitle: String,
    groups: List<AppNotificationGroup>,
): String {
    val notificationCount = groups.sumOf { group -> group.count }
    return if (notificationCount > 0) {
        "$baseTitle ($notificationCount)"
    } else {
        baseTitle
    }
}

@Composable
private fun NotificationCategorySummary(categoryCounts: Map<NotificationCategory, Int>) {
    Text(
        text = categoryCounts.summaryLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
    )
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
                text = group.metadataLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = group.latestAgeBucket.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.dismissibleNotificationKeys.isNotEmpty()) {
            TextButton(
                onClick = {
                    onAction(LauncherShellAction.DismissNotifications(group.dismissibleNotificationKeys))
                },
            ) {
                Text(text = "Clear")
            }
        }
        NotificationCountBadge(count = group.count)
    }
}

private fun InstalledApp.matches(group: AppNotificationGroup): Boolean =
    identity.packageName == group.packageName && identity.profile.id == group.profileId

private val AppNotificationGroup.metadataLabel: String
    get() = "${packageName.value} - ${latestCategory.label} - ${highestPriority.label} - $clearableLabel"

private val Map<NotificationCategory, Int>.summaryLabel: String
    get() =
        entries
            .sortedWith(
                compareByDescending<Map.Entry<NotificationCategory, Int>> { entry -> entry.value }
                    .thenBy { entry -> entry.key.label },
            )
            .take(MAX_CATEGORY_SUMMARY_ITEMS)
            .joinToString(separator = " - ") { (category, count) -> "${category.label} $count" }

private const val MAX_CATEGORY_SUMMARY_ITEMS = 4

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

private val NotificationPriority.label: String
    get() =
        when (this) {
            NotificationPriority.UNKNOWN -> "Priority unknown"
            NotificationPriority.MIN -> "Min priority"
            NotificationPriority.LOW -> "Low priority"
            NotificationPriority.DEFAULT -> "Default priority"
            NotificationPriority.HIGH -> "High priority"
            NotificationPriority.MAX -> "Max priority"
        }

@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory

data class NotificationOverviewPresentation(
    val apps: List<InstalledApp>,
    val appIconLoader: AppIconLoader,
    val reducedMotion: Boolean,
    val deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
)

@Composable
fun NotificationOverviewSurface(
    groups: List<AppNotificationGroup>,
    categoryCounts: Map<NotificationCategory, Int>,
    notificationAccessStatus: NotificationAccessStatus,
    presentation: NotificationOverviewPresentation,
    onAction: (LauncherShellAction) -> Unit,
    title: String = "Notifications",
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    var selectedCategory by remember { mutableStateOf<NotificationCategory?>(null) }
    var selectedGroupKey by remember { mutableStateOf<AppNotificationGroupKey?>(null) }
    val categoryOptions = notificationCategoryFilterOptions(groups)
    val effectiveSelectedCategory =
        selectedCategory.takeIf { category -> categoryOptions.any { option -> option.category == category } }
    val visibleGroups = notificationGroupsMatchingCategory(groups, effectiveSelectedCategory)
    val selectedGroup =
        selectedGroupKey?.let { key ->
            visibleGroups.firstOrNull { group -> group.key == key }
        }
    val selectedApp =
        selectedGroup?.let { group -> presentation.apps.firstOrNull { app -> app.matches(group) } }
    val panelTitle =
        selectedGroup?.let { group ->
            "${notificationOverviewGroupLabel(app = selectedApp, group = group)} (${group.count})"
        } ?: notificationOverviewTitle(baseTitle = title, groups = groups)

    LauncherPanel(
        title = panelTitle,
        onAction = onAction,
        windowInsets = windowInsets,
    ) {
        AnimatedContent(
            targetState = selectedGroup != null,
            transitionSpec = {
                if (presentation.reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(animationSpec = tween(CARD_EXPANSION_DURATION_MILLIS)) togetherWith
                        fadeOut(animationSpec = tween(CARD_EXPANSION_DURATION_MILLIS))
                }
            },
            label = "notification-card-expansion",
        ) { isCardExpanded ->
            when {
                groups.isEmpty() ->
                    EmptyNotifications(
                        notificationAccessStatus = notificationAccessStatus,
                        onAction = onAction,
                    )

                isCardExpanded && selectedGroup != null ->
                    NotificationGroupPrototype(
                        groups = visibleGroups,
                        selectedGroupKey = selectedGroup.key,
                        presentation = presentation,
                        onBack = { selectedGroupKey = null },
                        onGroupChanged = { groupKey -> selectedGroupKey = groupKey },
                        onAction = onAction,
                    )

                else ->
                    NotificationCardList(
                        groups = visibleGroups,
                        categoryCounts = categoryCounts,
                        categoryOptions = categoryOptions,
                        selectedCategory = effectiveSelectedCategory,
                        appIconLoader = presentation.appIconLoader,
                        apps = presentation.apps,
                        onCategorySelected = { category -> selectedCategory = category },
                        onOpenGroup = { groupKey -> selectedGroupKey = groupKey },
                        onAction = onAction,
                    )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun NotificationCardList(
    groups: List<AppNotificationGroup>,
    categoryCounts: Map<NotificationCategory, Int>,
    categoryOptions: List<NotificationCategoryOption>,
    selectedCategory: NotificationCategory?,
    apps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onCategorySelected: (NotificationCategory?) -> Unit,
    onOpenGroup: (AppNotificationGroupKey) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationCategorySummary(categoryCounts = categoryCounts)
                NotificationCategoryFilter(
                    options = categoryOptions,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                )
            }
        }
        items(
            items = groups,
            key = { group -> "${group.profileId.value}:${group.packageName.value}" },
        ) { group ->
            NotificationGroupRow(
                group = group,
                app = apps.firstOrNull { app -> app.matches(group) },
                appIconLoader = appIconLoader,
                onOpenGroup = { onOpenGroup(group.key) },
                onAction = onAction,
            )
        }
    }
}

internal fun notificationGroupsMatchingCategory(
    groups: List<AppNotificationGroup>,
    category: NotificationCategory?,
): List<AppNotificationGroup> =
    when (category) {
        null -> groups
        else -> groups.filter { group -> group.latestCategory == category }
    }

internal data class NotificationCategoryOption(
    val category: NotificationCategory?,
    val label: String,
)

internal fun notificationCategoryFilterOptions(groups: List<AppNotificationGroup>): List<NotificationCategoryOption> {
    val totalCount = groups.sumOf { group -> group.count }
    if (totalCount == 0) return emptyList()

    val categoryOptions =
        groups
            .groupBy { group -> group.latestCategory }
            .map { (category, categoryGroups) -> category to categoryGroups.sumOf { group -> group.count } }
            .sortedWith(
                compareByDescending<Pair<NotificationCategory, Int>> { pair -> pair.second }
                    .thenBy { pair -> pair.first.label },
            )
            .map { (category, count) ->
                NotificationCategoryOption(
                    category = category,
                    label = "${category.label} $count",
                )
            }

    return listOf(NotificationCategoryOption(category = null, label = "All $totalCount")) + categoryOptions
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
private fun NotificationCategoryFilter(
    options: List<NotificationCategoryOption>,
    selectedCategory: NotificationCategory?,
    onCategorySelected: (NotificationCategory?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = options,
            key = { option -> option.category?.name ?: "all" },
        ) { option ->
            FilterChip(
                selected = option.category == selectedCategory,
                onClick = { onCategorySelected(option.category) },
                label = { Text(text = option.label) },
            )
        }
    }
}

@Composable
private fun EmptyNotifications(
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    val actionLabel = notificationAccessStatus.emptyNotificationOverviewActionLabel

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = notificationAccessStatus.emptyNotificationOverviewLabel,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (actionLabel != null) {
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                Text(text = actionLabel)
            }
        }
    }
}

internal val NotificationAccessStatus.emptyNotificationOverviewLabel: String
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> "No active notifications"
            NotificationAccessStatus.NOT_GRANTED -> "Notification access is not allowed"
            NotificationAccessStatus.REVOKED -> "Notification access was revoked"
            NotificationAccessStatus.UNKNOWN -> "Notification access has not been checked"
        }

internal val NotificationAccessStatus.emptyNotificationOverviewActionLabel: String?
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> null
            NotificationAccessStatus.NOT_GRANTED,
            NotificationAccessStatus.REVOKED,
            NotificationAccessStatus.UNKNOWN,
            -> "Open notification access"
        }

@Composable
private fun NotificationGroupRow(
    group: AppNotificationGroup,
    app: InstalledApp?,
    appIconLoader: AppIconLoader,
    onOpenGroup: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 96.dp)
                .clickable(onClick = onOpenGroup),
        shape = LocalLauncherCardShape.current,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
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
                    val label = notificationOverviewGroupLabel(app = app, group = group)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = group.notificationOverviewMetadataLabel(label),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                app?.let { matchedApp ->
                    TextButton(onClick = { onAction(LauncherShellAction.LaunchApp(matchedApp.identity)) }) {
                        Text(text = "Open app")
                    }
                }
                TextButton(onClick = onOpenGroup) {
                    Text(text = "View card")
                }
                if (group.dismissibleNotificationKeys.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onAction(LauncherShellAction.DismissNotifications(group.dismissibleNotificationKeys))
                        },
                    ) {
                        Text(text = "Clear")
                    }
                }
            }
        }
    }
}

internal fun InstalledApp.matches(group: AppNotificationGroup): Boolean =
    identity.packageName == group.packageName && identity.profile.id == group.profileId

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
private const val CARD_EXPANSION_DURATION_MILLIS = 220

internal val NotificationAgeBucket.label: String
    get() =
        when (this) {
            NotificationAgeBucket.NOW -> "Now"
            NotificationAgeBucket.RECENT -> "Recent"
            NotificationAgeBucket.TODAY -> "Today"
            NotificationAgeBucket.OLDER -> "Older"
        }

internal val NotificationCategory.label: String
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

internal fun notificationOverviewFocusedNotification(
    notifications: List<LauncherNotification>,
    firstVisibleItemIndex: Int,
): LauncherNotification? =
    notifications.getOrNull(firstVisibleItemIndex.coerceAtLeast(0))
        ?: notifications.lastOrNull()

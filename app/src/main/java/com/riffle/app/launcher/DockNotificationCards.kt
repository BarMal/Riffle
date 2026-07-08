package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

internal sealed interface DockNotificationShelfState {
    data object Hidden : DockNotificationShelfState

    data class PermissionPrompt(
        val label: String,
        val actionLabel: String,
    ) : DockNotificationShelfState

    data class Content(
        val cards: List<DockNotificationCardState>,
    ) : DockNotificationShelfState
}

internal data class DockNotificationCardState(
    val app: InstalledApp?,
    val group: AppNotificationGroup,
) {
    val clearAction: LauncherShellAction.DismissNotifications?
        get() =
            group.dismissibleNotificationKeys
                .takeIf { keys -> keys.isNotEmpty() }
                ?.let { keys -> LauncherShellAction.DismissNotifications(keys) }
}

internal fun dockNotificationShelfState(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
    maxCards: Int = MAX_DOCK_NOTIFICATION_CARDS,
): DockNotificationShelfState {
    val permissionPromptState =
        notificationAccessStatus.emptyNotificationOverviewActionLabel?.let { actionLabel ->
            DockNotificationShelfState.PermissionPrompt(
                label = notificationAccessStatus.emptyNotificationOverviewLabel,
                actionLabel = actionLabel,
            )
        }

    return when {
        notificationAccessStatus != NotificationAccessStatus.GRANTED ->
            permissionPromptState ?: DockNotificationShelfState.Hidden

        groups.isEmpty() || maxCards <= 0 -> DockNotificationShelfState.Hidden

        else ->
            DockNotificationShelfState.Content(
                cards =
                    groups.take(maxCards).map { group ->
                        DockNotificationCardState(
                            app = apps.firstOrNull { app -> app.matches(group) },
                            group = group,
                        )
                    },
            )
    }
}

@Composable
internal fun DockNotificationShelf(
    state: DockNotificationShelfState,
    appIconLoader: AppIconLoader,
    interactions: DockInteractions,
) {
    when (state) {
        DockNotificationShelfState.Hidden -> Unit
        is DockNotificationShelfState.PermissionPrompt ->
            DockNotificationPermissionCard(
                state = state,
                interactions = interactions,
            )
        is DockNotificationShelfState.Content ->
            DockNotificationCardRow(
                cards = state.cards,
                appIconLoader = appIconLoader,
                interactions = interactions,
            )
    }
}

@Composable
private fun DockNotificationPermissionCard(
    state: DockNotificationShelfState.PermissionPrompt,
    interactions: DockInteractions,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .dockShelfGestureInput(interactions)
                .clickable(onClick = { interactions.onAction(LauncherShellAction.RequestNotificationAccess) }),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DOCK_NOTIFICATION_CARD_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = state.actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DockNotificationCardRow(
    cards: List<DockNotificationCardState>,
    appIconLoader: AppIconLoader,
    interactions: DockInteractions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .dockShelfGestureInput(interactions)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { card ->
            DockNotificationCard(
                card = card,
                appIconLoader = appIconLoader,
                onAction = interactions.onAction,
            )
        }
    }
}

@Composable
private fun DockNotificationCard(
    card: DockNotificationCardState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label = card.app?.label ?: card.group.packageName.value
    val identity = card.app?.identity

    Surface(
        modifier =
            Modifier
                .defaultMinSize(minWidth = 164.dp, minHeight = 84.dp)
                .clickable(enabled = identity != null) {
                    identity?.let { appIdentity -> onAction(LauncherShellAction.LaunchApp(appIdentity)) }
                },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DOCK_NOTIFICATION_CARD_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockNotificationIcon(
                    identity = identity,
                    label = label,
                    appIconLoader = appIconLoader,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = "${card.group.latestCategory.label} - ${card.group.latestAgeBucket.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                NotificationCountBadge(count = card.group.count)
            }
            Text(
                text = dockNotificationCardSummary(card.group),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            card.clearAction?.let { clearAction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onAction(clearAction) }) {
                        Text(text = "Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun DockNotificationIcon(
    identity: AppIdentity?,
    label: String,
    appIconLoader: AppIconLoader,
) {
    if (identity != null) {
        LauncherAppIcon(
            identity = identity,
            label = label,
            iconLoader = appIconLoader,
            modifier = Modifier.requiredSize(28.dp),
            shape = CircleShape,
        )
        return
    }

    Box(
        modifier =
            Modifier
                .requiredSize(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal fun dockNotificationCardSummary(group: AppNotificationGroup): String =
    when {
        group.clearableCount == 0 -> "Pinned notifications"
        group.clearableCount == group.count -> "Tap to open or clear"
        else -> "${group.clearableCount} clearable of ${group.count}"
    }

private const val MAX_DOCK_NOTIFICATION_CARDS = 3
private const val DOCK_NOTIFICATION_CARD_ALPHA = 0.78f

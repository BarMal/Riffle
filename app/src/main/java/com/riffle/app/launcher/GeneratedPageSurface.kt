package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun GeneratedNotificationCardsPage(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = generatedNotificationCardsPageState(groups, notificationAccessStatus, apps)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state) {
            is GeneratedNotificationCardsPageState.Content ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Notification cards page" },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { GeneratedCardsHeading() }
                    items(state.cards, key = { card -> card.group.packageName.value + card.group.profileId.value }) { card ->
                        GeneratedNotificationCard(card, onAction)
                    }
                }

            is GeneratedNotificationCardsPageState.Message ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = state.title, style = MaterialTheme.typography.titleLarge)
                    Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                    if (notificationAccessStatus != NotificationAccessStatus.GRANTED) {
                        TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                            Text(text = "Allow notification access")
                        }
                    }
                }
        }
    }
}

@Composable
private fun GeneratedCardsHeading() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text = "Cards", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Your current notifications", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GeneratedNotificationCard(
    card: DockNotificationCardState,
    onAction: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = LocalLauncherCardShape.current,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = card.app?.label ?: card.group.packageName.value, style = MaterialTheme.typography.titleMedium)
            Text(
                text = dockNotificationCardSummary(card.group, canLaunchApp = card.app != null),
                style = MaterialTheme.typography.bodyMedium,
            )
            card.clearAction?.let { action ->
                TextButton(onClick = { onAction(action) }) {
                    Text(text = "Clear")
                }
            }
        }
    }
}

internal sealed interface GeneratedNotificationCardsPageState {
    data class Content(val cards: List<DockNotificationCardState>) : GeneratedNotificationCardsPageState

    data class Message(val title: String, val message: String) : GeneratedNotificationCardsPageState
}

internal fun generatedNotificationCardsPageState(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
): GeneratedNotificationCardsPageState =
    when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED ->
            if (groups.isEmpty()) {
                GeneratedNotificationCardsPageState.Message("No notifications", "New notifications will appear here.")
            } else {
                GeneratedNotificationCardsPageState.Content(
                    groups.map { group ->
                        DockNotificationCardState(app = apps.firstOrNull { app -> app.matches(group) }, group = group)
                    },
                )
            }

        else ->
            GeneratedNotificationCardsPageState.Message(
                "Notification access needed",
                "Allow notification access to show your notification cards.",
            )
    }

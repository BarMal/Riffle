package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun GeneratedNotificationCardsPage(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = generatedNotificationCardsPageState(groups, notificationAccessStatus, apps)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state) {
            is GeneratedNotificationCardsPageState.Content ->
                Column(
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Notification cards page" },
                ) {
                    GeneratedCardsHeading()
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        CardStack(
                            entries = generatedNotificationCardStackEntries(state.cards),
                            modifier = Modifier.fillMaxSize(),
                            animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
                            reducedMotion = reducedMotion,
                            itemKey = { entry -> generatedNotificationCardKey(state.cards[entry.cardIndex].group) },
                        ) { entry ->
                            GeneratedNotificationCard(
                                card = state.cards[entry.cardIndex],
                                onAction = onAction,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
    modifier: Modifier = Modifier,
) {
    val label = dockNotificationCardLabel(card)
    val identity = card.app?.identity
    Surface(
        modifier =
            Modifier
                .then(modifier)
                .semantics {
                    contentDescription = generatedNotificationCardContentDescription(card)
                }
                .clickable(enabled = identity != null) {
                    generatedNotificationCardLaunchAction(card)?.let(onAction)
                },
        shape = LocalLauncherCardShape.current,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = dockNotificationCardSummary(card.group, canLaunchApp = card.app != null),
                style = MaterialTheme.typography.bodyMedium,
            )
            card.clearAction?.let { action ->
                TextButton(
                    onClick = { onAction(action) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = generatedNotificationCardClearContentDescription(card)
                        },
                ) {
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

internal fun generatedNotificationCardKey(group: AppNotificationGroup): AppNotificationGroupKey =
    AppNotificationGroupKey(packageName = group.packageName, profileId = group.profileId)

internal fun generatedNotificationCardClearContentDescription(card: DockNotificationCardState): String =
    dockNotificationClearContentDescription(
        label = dockNotificationCardLabel(card),
        clearableCount = card.group.clearableCount,
    )

internal fun generatedNotificationCardContentDescription(card: DockNotificationCardState): String =
    dockNotificationCardContentDescription(
        card = card,
        label = dockNotificationCardLabel(card),
    )

internal fun generatedNotificationCardStackEntries(
    cards: List<DockNotificationCardState>,
    focusedCardIndex: Int = 0,
): List<CardStackLayoutEntry> =
    CardStackLayoutPolicy().entries(
        cardCount = cards.size,
        activeIndex = focusedCardIndex,
    )

internal fun generatedNotificationCardLaunchAction(card: DockNotificationCardState): LauncherShellAction.LaunchApp? =
    card.app?.identity?.let(LauncherShellAction::LaunchApp)

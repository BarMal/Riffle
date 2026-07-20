@file:Suppress("LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AndroidNotificationStageActionGateway
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.AppStageShellStateReconciler
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.TimeScapeViewportDp
import com.riffle.core.domain.launcher.settings.resolveTimeScapeCardStack

/** The single reachable Cards home surface for compact TimeScape mode. */
@Composable
internal fun TimeScapeAppStageSurface(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    val reconciler = remember { AppStageShellStateReconciler(AndroidNotificationStageActionGateway) }
    val shellState = reconciler.reconcile(state)
    val selectedStage = shellState.snapshot.selectedStage

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeScapeStageHeader(selectedStage, state, onAction)
            if (selectedStage == null) {
                TimeScapeUnavailableState(state.notificationAccessStatus, onAction, Modifier.weight(1f))
            } else {
                TimeScapeStageContent(
                    stage = selectedStage,
                    state = state,
                    shellState = shellState,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            }
            TimeScapeStageSelector(shellState.snapshot.stages, selectedStage?.id, state, onAction)
        }
    }
}

@Composable
private fun TimeScapeStageHeader(
    selectedStage: AppStage?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label = selectedStage?.let { stageLabel(it.id, state) } ?: "TimeScape"
    var overflowExpanded by rememberSaveable(selectedStage?.let(::timeScapeStageSelectorItemKey)) {
        mutableStateOf(false)
    }
    val selectedApp =
        selectedStage?.let { stage ->
            state.installedApps.firstOrNull { app ->
                app.identity.packageName == stage.id.packageName &&
                    app.identity.profile.id == stage.id.profileId
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Text(text = "TimeScape", style = MaterialTheme.typography.labelMedium)
        }
        if (selectedStage != null) {
            TextButton(onClick = { onAction(LauncherShellAction.ToggleAppStagePinned(selectedStage.id)) }) {
                Text(if (selectedStage.isPinned) "Unpin" else "Pin")
            }
            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "More stage options" },
                ) {
                    Text(text = "⋮")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (selectedStage.isPinned) "Unpin stage" else "Pin stage") },
                        onClick = {
                            overflowExpanded = false
                            onAction(LauncherShellAction.ToggleAppStagePinned(selectedStage.id))
                        },
                    )
                    selectedApp?.let { app ->
                        DropdownMenuItem(
                            text = { Text("Open ${app.label}") },
                            onClick = {
                                overflowExpanded = false
                                onAction(LauncherShellAction.LaunchApp(app.identity))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("App info") },
                            onClick = {
                                overflowExpanded = false
                                onAction(LauncherShellAction.OpenAppInfo(app.identity))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeScapeStageContent(
    stage: AppStage,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    when {
        stage.content.isEmpty() ->
            TimeScapeEmptyStage(stage, shellState, onAction, modifier)
        else ->
            TimeScapeNotificationStack(stage, state, shellState.notificationCards, onAction, modifier)
    }
}

@Composable
private fun TimeScapeNotificationStack(
    stage: AppStage,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val cards =
        stage.content.mapNotNull { content ->
            notificationCards.firstOrNull { it.content.id == content.id }
        }
    var focusedCardIdValue by rememberSaveable(stage.id.profileId.value, stage.id.packageName.value) {
        mutableStateOf(cards.firstOrNull()?.content?.id?.value)
    }
    val focusedCard =
        cards.firstOrNull { it.content.id.value == focusedCardIdValue } ?: cards.firstOrNull()
    if (focusedCard != null && focusedCardIdValue != focusedCard.content.id.value) {
        focusedCardIdValue = focusedCard.content.id.value
    }
    val activeCard = focusedCard ?: return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val resolution =
            state.launcherSettings.resolveTimeScapeCardStack(
                TimeScapeViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt()),
            )
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CardStack(
                    entries = resolution.layoutPolicy.entries(cards.size, cards.indexOf(activeCard)),
                    animationSpec = resolution.animation,
                    reducedMotion = resolution.reducedMotion,
                    itemKey = { entry -> cards[entry.cardIndex].content.id },
                    interaction =
                        CardStackInteraction(
                            focusedItemKey = activeCard.content.id,
                            onFocusRequest = { entry ->
                                focusedCardIdValue = cards[entry.cardIndex].content.id.value
                            },
                            onSettle = { drag, velocity ->
                                val currentIndex = cards.indexOfFirst { it.content.id == activeCard.content.id }
                                val targetIndex =
                                    when {
                                        velocity < -500f || drag < -64f -> currentIndex + 1
                                        velocity > 500f || drag > 64f -> currentIndex - 1
                                        else -> currentIndex
                                    }
                                cards.getOrNull(targetIndex)?.let { card ->
                                    focusedCardIdValue = card.content.id.value
                                }
                            },
                        ),
                ) { entry, cardModifier ->
                    val card = cards[entry.cardIndex]
                    TimeScapeCardSurface(
                        appearance = state.launcherSettings.cards.timeScapeAppearance,
                        background = TimeScapeCardBackground(appSeed = stage.id.packageName.value),
                        modifier =
                            cardModifier.size(
                                width = resolution.cardWidthDp.dp,
                                height = resolution.cardHeightDp.dp,
                            ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium)
                            Text(card.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            TimeScapeContextShelf(activeCard, onAction)
        }
    }
}

@Composable
private fun TimeScapeContextShelf(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (card.supportedActions.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        card.supportedActions.sortedBy { action -> action.label() }.forEach { action ->
            TextButton(
                onClick = {
                    onAction(LauncherShellAction.PerformNotificationStageAction(card.notificationKey, action))
                },
            ) {
                Text(action.label())
            }
        }
    }
}

@Composable
private fun TimeScapeEmptyStage(
    stage: AppStage,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val emptyCard = shellState.emptyAppCards[stage.id]
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (stage.lifecycle.name == "PROFILE_LOCKED") "Profile unavailable" else "Nothing new",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("This stage stays available so you can return to it.", style = MaterialTheme.typography.bodyMedium)
        emptyCard?.let { card ->
            TextButton(onClick = { onAction(LauncherShellAction.LaunchApp(card.app.identity)) }) {
                Text("Open ${card.app.label}")
            }
            card.shortcuts.forEach { shortcut ->
                TextButton(onClick = { onAction(LauncherShellAction.LaunchAppShortcut(shortcut)) }) {
                    Text(shortcut.shortLabel)
                }
            }
        }
    }
}

@Composable
private fun TimeScapeUnavailableState(
    access: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val message =
        when (access) {
            NotificationAccessStatus.GRANTED -> "No active stages yet. New notifications will appear here."
            NotificationAccessStatus.NOT_GRANTED -> "Allow notification access to show your app stages."
            NotificationAccessStatus.REVOKED -> "Notification access was revoked. Restore access to update stages."
            NotificationAccessStatus.UNKNOWN -> "Checking notification access."
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (access == NotificationAccessStatus.NOT_GRANTED || access == NotificationAccessStatus.REVOKED) {
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                Text("Allow access")
            }
        }
    }
}

@Composable
private fun TimeScapeStageSelector(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (stages.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onAction(LauncherShellAction.SelectPreviousAppStage) }) { Text("Previous") }
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stages, key = ::timeScapeStageSelectorItemKey) { stage ->
                TextButton(
                    onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "${stageLabel(stage.id, state)}" +
                                if (stage.id == selectedStageId) {
                                    ", selected. Open stage"
                                } else {
                                    ". Open stage"
                                }
                        },
                ) { Text(stageLabel(stage.id, state)) }
            }
        }
        TextButton(onClick = { onAction(LauncherShellAction.SelectNextAppStage) }) { Text("Next") }
    }
}

/** Lazy layouts require item keys that Android can store in a Bundle across recreation. */
internal fun timeScapeStageSelectorItemKey(stage: AppStage): String {
    return "${stage.id.profileId.value}:${stage.id.packageName.value}"
}

private fun stageLabel(
    id: AppStageId,
    state: LauncherShellState,
): String =
    state.installedApps.firstOrNull { app ->
        app.identity.packageName == id.packageName && app.identity.profile.id == id.profileId
    }?.let { app ->
        app.identity.profile.profileDisplayLabel(app.label)
    } ?: "${id.packageName.value} (${id.profileId.value})"

private fun NotificationStageAction.label(): String =
    when (this) {
        NotificationStageAction.Open -> "Open"
        NotificationStageAction.Dismiss -> "Dismiss"
        is NotificationStageAction.MediaControl ->
            command.name.lowercase().replaceFirstChar { character -> character.titlecase() }
        is NotificationStageAction.ProviderAction -> "Action"
    }

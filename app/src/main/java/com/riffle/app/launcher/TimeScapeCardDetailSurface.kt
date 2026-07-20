@file:Suppress("MatchingDeclarationName")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardExpansionPhase
import com.riffle.core.domain.launcher.cards.CardExpansionState
import com.riffle.core.domain.launcher.cards.LauncherCardId
import kotlinx.coroutines.delay

/**
 * Transient, saveable detail ownership for one visible app stage.
 *
 * Source payload and platform action handles stay with the live stage projection. Only the stable
 * card identity and presentation phase survive activity recreation.
 */
internal class TimeScapeCardDetailState(
    private val currentExpansion: () -> CardExpansionState,
    private val updateExpansion: (CardExpansionState) -> Unit,
    private val currentRecoveryMessage: () -> String?,
    private val updateRecoveryMessage: (String?) -> Unit,
    val reducedMotion: Boolean,
) {
    val expansionState: CardExpansionState
        get() = currentExpansion()

    val sourceRemovalMessage: String?
        get() = currentRecoveryMessage()

    fun expand(cardId: LauncherCardId) {
        updateRecoveryMessage(null)
        updateExpansion(expansionState.expand(cardId, reducedMotion))
    }

    fun close() {
        updateExpansion(expansionState.collapse(reducedMotion))
    }

    fun completeTransition() {
        updateExpansion(expansionState.complete())
    }

    fun reconcile(availableCardIds: Set<LauncherCardId>) {
        val previous = expansionState
        val reconciled = previous.reconcile(availableCardIds, reducedMotion)
        updateExpansion(reconciled)
        if (previous.isVisible && !reconciled.isVisible) {
            updateRecoveryMessage("The selected card is no longer available.")
        }
    }
}

@Composable
internal fun rememberTimeScapeCardDetailState(
    stageId: AppStageId,
    reducedMotion: Boolean,
): TimeScapeCardDetailState {
    var expansion by
        rememberSaveable(
            stageId.profileId.value,
            stageId.packageName.value,
            stateSaver = CardExpansionStateSaver,
        ) {
            mutableStateOf(CardExpansionState())
        }
    var recoveryMessage by rememberSaveable(stageId.profileId.value, stageId.packageName.value) {
        mutableStateOf<String?>(null)
    }
    return remember(stageId, reducedMotion) {
        TimeScapeCardDetailState(
            currentExpansion = { expansion },
            updateExpansion = { expansion = it },
            currentRecoveryMessage = { recoveryMessage },
            updateRecoveryMessage = { recoveryMessage = it },
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
internal fun TimeScapeCardDetailSurface(
    card: AppStageNotificationCard,
    detailState: TimeScapeCardDetailState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    TimeScapeDetailContainer(detailState = detailState, modifier = modifier) {
        Text(detailTitle(card), style = MaterialTheme.typography.headlineSmall)
        Text(detailKindLabel(card.content.kind), style = MaterialTheme.typography.labelLarge)
        Text(card.text, style = MaterialTheme.typography.bodyLarge)
        TimeScapeContextShelf(card = card, onAction = onAction)
    }
}

@Composable
internal fun TimeScapeEmptyAppDetailSurface(
    card: AppStageEmptyAppCard,
    detailState: TimeScapeCardDetailState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    TimeScapeDetailContainer(detailState = detailState, modifier = modifier) {
        Text("${card.app.label} details", style = MaterialTheme.typography.headlineSmall)
        Text("App details", style = MaterialTheme.typography.labelLarge)
        Text("No current notification content for this app.", style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun TimeScapeDetailContainer(
    detailState: TimeScapeCardDetailState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val phase = detailState.expansionState.phase
    val alpha by
        animateFloatAsState(
            targetValue = if (phase == CardExpansionPhase.COLLAPSING) 0f else 1f,
            animationSpec = if (detailState.reducedMotion) snap() else tween(DETAIL_TRANSITION_MILLIS),
            label = "timescape-card-detail-alpha",
        )

    BackHandler(enabled = detailState.expansionState.isVisible) { detailState.close() }

    LaunchedEffect(phase, detailState.reducedMotion) {
        if (phase == CardExpansionPhase.EXPANDING || phase == CardExpansionPhase.COLLAPSING) {
            if (!detailState.reducedMotion) delay(DETAIL_TRANSITION_MILLIS.toLong())
            detailState.completeTransition()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = detailState::close) { Text("Back") }
        content()
    }
}

@Composable
internal fun TimeScapeDetailRecoveryMessage(
    message: String?,
    modifier: Modifier = Modifier,
) {
    message ?: return
    Text(
        text = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun detailTitle(card: AppStageNotificationCard): String =
    when (card.content.kind) {
        AppStageContentKind.NOTIFICATION -> card.title
        AppStageContentKind.MEDIA -> "Now playing: ${card.title}"
    }

private fun detailKindLabel(kind: AppStageContentKind): String =
    when (kind) {
        AppStageContentKind.NOTIFICATION -> "Notification details"
        AppStageContentKind.MEDIA -> "Media details"
    }

private val CardExpansionStateSaver =
    Saver<CardExpansionState, List<String>>(
        save = { state -> listOf(state.phase.name, state.cardId?.value.orEmpty()) },
        restore = { saved ->
            saved.getOrNull(0)
                ?.let { name -> runCatching { CardExpansionPhase.valueOf(name) }.getOrNull() }
                ?.let { phase ->
                    val cardId = saved.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::LauncherCardId)
                    runCatching { CardExpansionState(phase, cardId) }.getOrNull()
                }
        },
    )

private const val DETAIL_TRANSITION_MILLIS = 150

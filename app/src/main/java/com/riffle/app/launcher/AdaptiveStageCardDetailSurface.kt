@file:Suppress("MatchingDeclarationName")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.CardExpansionPhase
import com.riffle.core.domain.launcher.cards.CardExpansionState
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.settings.AdaptiveStageMotion
import kotlinx.coroutines.delay

/**
 * Transient, saveable detail ownership for one visible app stage.
 *
 * Source payload and platform action handles stay with the live stage projection. Only the stable
 * card identity and presentation phase survive activity recreation.
 */
internal class AdaptiveStageCardDetailState(
    private val currentExpansion: () -> CardExpansionState,
    private val updateExpansion: (CardExpansionState) -> Unit,
    private val currentRecoveryMessage: () -> String?,
    private val updateRecoveryMessage: (String?) -> Unit,
    val motion: AdaptiveStageMotion,
    val globalReducedMotion: Boolean,
) {
    val reducedMotion: Boolean
        get() = motion.reducedMotion || globalReducedMotion
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

/**
 * [scopeKey] gives this state its own independent saved slot -- e.g. a stage's id for a real stage,
 * or a fixed constant for a single shared surface like the merged All-notifications view.
 * Rendering itself never needs [scopeKey]: the card passed to [AdaptiveStageCardDetailSurface]
 * already carries its own stage attribution via `AppStageContent.stageId`, so this state machine is
 * deliberately stage-agnostic -- it only tracks which card id is expanded, not which stage it's from.
 */
@Composable
internal fun rememberAdaptiveStageCardDetailState(
    scopeKey: Any,
    motion: AdaptiveStageMotion,
    globalReducedMotion: Boolean = false,
): AdaptiveStageCardDetailState {
    var expansion by
        rememberSaveable(scopeKey, stateSaver = CardExpansionStateSaver) {
            mutableStateOf(CardExpansionState())
        }
    var recoveryMessage by rememberSaveable(scopeKey) {
        mutableStateOf<String?>(null)
    }
    return remember(scopeKey, motion, globalReducedMotion) {
        AdaptiveStageCardDetailState(
            currentExpansion = { expansion },
            updateExpansion = { expansion = it },
            currentRecoveryMessage = { recoveryMessage },
            updateRecoveryMessage = { recoveryMessage = it },
            motion = motion,
            globalReducedMotion = globalReducedMotion,
        )
    }
}

@Composable
internal fun AdaptiveStageCardDetailSurface(
    card: AppStageNotificationCard,
    detailState: AdaptiveStageCardDetailState,
    onAction: (LauncherShellAction) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AdaptiveStageDetailContainer(detailState = detailState, onClose = onClose, modifier = modifier) {
        Text(detailTitle(card), style = MaterialTheme.typography.headlineSmall)
        Text(detailKindLabel(card.content.kind), style = MaterialTheme.typography.labelLarge)
        AdaptiveStageCardMessageBody(card, style = MaterialTheme.typography.bodyLarge)
        AdaptiveStageContextShelf(card = card, onAction = onAction)
    }
}

@Composable
internal fun AdaptiveStageEmptyAppDetailSurface(
    card: AppStageEmptyAppCard,
    detailState: AdaptiveStageCardDetailState,
    onAction: (LauncherShellAction) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AdaptiveStageDetailContainer(detailState = detailState, onClose = onClose, modifier = modifier) {
        Text("${card.app.label} details", style = MaterialTheme.typography.headlineSmall)
        Text("App details", style = MaterialTheme.typography.labelLarge)
        Text("No current notification content for this app.", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onAction(LauncherShellAction.LaunchApp(card.app.identity)) }) {
            Text("Open ${card.app.label}")
        }
        if (card.shortcuts.isNotEmpty()) {
            EmptyStageQuickActionsCardStack(
                shortcuts = card.shortcuts,
                reducedMotion = detailState.reducedMotion,
                onAction = onAction,
            )
        }
    }
}

/**
 * A quiet stage's quick actions (its app's own shortcuts), fanned as a small static card stack
 * instead of a flat button list -- every card is fully visible and directly tappable at once (no
 * drag-to-navigate: there is nothing to bring to focus, each one already dispatches its own
 * shortcut), so this reuses only [CardStackLayoutPolicy]'s pure fan geometry, not the interactive
 * [CardStack] built for the primary stage stack.
 */
@Composable
private fun EmptyStageQuickActionsCardStack(
    shortcuts: List<AppShortcut>,
    reducedMotion: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    val policy =
        remember(shortcuts.size) {
            CardStackLayoutPolicy(
                maxVisibleDepth = (shortcuts.size - 1).coerceAtLeast(0),
                scaleStep = QUICK_ACTION_STACK_SCALE_STEP,
                offsetStep = QUICK_ACTION_STACK_OFFSET_STEP,
                verticalOffsetStep = QUICK_ACTION_STACK_VERTICAL_OFFSET_STEP,
                rotationStep = QUICK_ACTION_STACK_ROTATION_STEP,
                alphaStep = QUICK_ACTION_STACK_ALPHA_STEP,
            )
        }
    // The front card (depth 0) sits at the top; the fan trails toward the bottom, so the extra
    // height the fan needs is reserved below it rather than split around it.
    val entries = remember(policy, shortcuts.size) { policy.entries(cardCount = shortcuts.size, activeIndex = 0) }
    val fanHeightDp =
        QUICK_ACTION_STACK_CARD_HEIGHT_DP +
            (QUICK_ACTION_STACK_VERTICAL_OFFSET_STEP * (shortcuts.size - 1).coerceAtLeast(0))

    Box(
        // Fanned entries translate past their own footprint by design (see CardStack.kt's own
        // clipToBounds doc) -- without this, that overflow would bleed into the "Open app" button
        // above or whatever the scrolling detail column holds below.
        modifier = Modifier.fillMaxWidth().height(fanHeightDp.dp).clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        entries.forEach { entry ->
            val shortcut = shortcuts[entry.cardIndex]
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(QUICK_ACTION_STACK_CARD_HEIGHT_DP.dp)
                        .zIndex(entry.order.toFloat())
                        .graphicsLayer {
                            translationX = entry.offset.dp.toPx()
                            translationY = entry.verticalOffset.dp.toPx()
                            scaleX = entry.scale
                            scaleY = entry.scale
                            rotationZ = if (reducedMotion) 0f else entry.rotationDegrees
                            alpha = entry.alpha
                        }
                        .clickable(onClick = { onAction(LauncherShellAction.LaunchAppShortcut(shortcut)) })
                        // Merges the card's own Text into one actionable node, the same way a
                        // Material Button already does for its label -- a screen reader announces
                        // one "Reply, button" rather than a click target with no label of its own.
                        .semantics(mergeDescendants = true) {},
                shape = RoundedCornerShape(QUICK_ACTION_STACK_CORNER_RADIUS_DP.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = QUICK_ACTION_STACK_ELEVATION_DP.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)) {
                    Text(shortcut.shortLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private const val QUICK_ACTION_STACK_CARD_HEIGHT_DP = 48
private const val QUICK_ACTION_STACK_CORNER_RADIUS_DP = 16
private const val QUICK_ACTION_STACK_ELEVATION_DP = 2
private const val QUICK_ACTION_STACK_SCALE_STEP = 0.05f
private const val QUICK_ACTION_STACK_OFFSET_STEP = 6f

// Each card's full-width Surface is its own touch target, and the nearer-to-focus card always
// draws (and hit-tests) on top -- so a card behind it is only reliably tappable at points its own
// center clears the card(s) in front, i.e. this step must exceed half of
// QUICK_ACTION_STACK_CARD_HEIGHT_DP. A value at or below that half-height would leave a trailing
// card's center still covered by the card in front of it, so its tap target would silently belong
// to the wrong card.
private const val QUICK_ACTION_STACK_VERTICAL_OFFSET_STEP = 30f
private const val QUICK_ACTION_STACK_ROTATION_STEP = 4f
private const val QUICK_ACTION_STACK_ALPHA_STEP = 0.12f

@Composable
private fun AdaptiveStageDetailContainer(
    detailState: AdaptiveStageCardDetailState,
    onClose: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val phase = detailState.expansionState.phase
    val alpha by
        animateFloatAsState(
            targetValue = if (phase == CardExpansionPhase.COLLAPSING) 0f else 1f,
            animationSpec =
                if (detailState.reducedMotion) {
                    snap()
                } else {
                    tween(detailState.transitionDurationMillis(phase))
                },
            label = "adaptive-stage-card-detail-alpha",
        )

    val closeDetail = {
        onClose()
        detailState.close()
    }
    BackHandler(enabled = detailState.expansionState.isVisible, onBack = closeDetail)

    LaunchedEffect(phase, detailState.reducedMotion) {
        if (phase == CardExpansionPhase.EXPANDING || phase == CardExpansionPhase.COLLAPSING) {
            if (!detailState.reducedMotion) delay(detailState.transitionDurationMillis(phase).toLong())
            detailState.completeTransition()
        }
    }

    // A solid backdrop, not just a fade -- without one, the stack behind (siblings dimmed but
    // still composed, plus the position indicator) shows straight through the detail content,
    // reading as illegible overlapping text rather than a card of its own.
    Surface(
        modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .semantics {
                        stateDescription = "Card details open"
                        liveRegion = LiveRegionMode.Polite
                    }
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = closeDetail) { Text("Back") }
            content()
        }
    }
}

internal fun AdaptiveStageCardDetailState.transitionDurationMillis(phase: CardExpansionPhase): Int =
    when (phase) {
        CardExpansionPhase.EXPANDING -> motion.expandDurationMillis
        CardExpansionPhase.COLLAPSING -> motion.exitDurationMillis
        CardExpansionPhase.COLLAPSED,
        CardExpansionPhase.EXPANDED,
        -> motion.expandDurationMillis
    }

@Composable
internal fun AdaptiveStageDetailRecoveryMessage(
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

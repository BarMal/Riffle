package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.LauncherCardId

/**
 * Resolves this composition's focused card id for a card stack, reconciling against [cardIds]
 * synchronously rather than in a `LaunchedEffect`. Every stack surface used to compute its
 * rendered index as `cardIds.indexOf(focusedCardId).takeIf { it >= 0 } ?: 0` during composition,
 * then correct a vanished focus (e.g. a dismissed card) only inside `LaunchedEffect(cardIds)` --
 * a coroutine that runs *after* that composition's frame. For one frame the stack rendered
 * against index 0 (whatever card happened to sort first) instead of the nearest surviving card,
 * then snapped to the corrected index moments later once the effect landed. Two different
 * animated target poses landing back-to-back for the same focus change is what read as the
 * focused card's position jumping. [CardStackController.reconcile] is a pure, cheap function --
 * there's no reason it can't run inline during composition instead.
 *
 * The reconciled id is also pushed to [onFocusedCardChanged] so any external owner (a repository,
 * a hoisted parent) stays in sync; that push is fine to stay async since it only affects later
 * frames, never the one currently rendering.
 */
@Composable
internal fun rememberReconciledFocusedCardId(
    controller: CardStackController,
    stackKey: CardStackKey,
    cardIds: List<LauncherCardId>,
    focusedCardId: LauncherCardId?,
    onFocusedCardChanged: (LauncherCardId?) -> Unit,
): LauncherCardId? {
    var previousCardIds by remember(controller) { mutableStateOf(cardIds) }
    val reconciledFocusedCardId =
        remember(controller, cardIds, focusedCardId) {
            val focusState = CardStackFocusState(stackKey, focusedCardId)
            val reconciliation =
                if (focusState.focusedCardId == null) {
                    controller.restore(focusState, cardIds)
                } else {
                    controller.reconcile(focusState, previousCardIds, cardIds)
                }
            val resolved =
                (reconciliation as? CardStackFocusResult.Applied)?.state?.focusedCardId
                    ?: focusState.focusedCardId
            previousCardIds = cardIds
            resolved
        }
    LaunchedEffect(reconciledFocusedCardId) {
        if (reconciledFocusedCardId != focusedCardId) onFocusedCardChanged(reconciledFocusedCardId)
    }
    return reconciledFocusedCardId
}

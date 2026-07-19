package com.riffle.core.domain.launcher.cards

import kotlin.math.abs

/** A durable identity for one stack surface; it is deliberately not a display label. */
@JvmInline
value class CardStackKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Card stack keys must not be blank." }
    }
}

/**
 * The persistable part of a stack's state.  It records card identity rather than a volatile list
 * position, so a caller can retain separate focus for each overview, chapter, page, or dock.
 */
data class CardStackFocusState(
    val stackKey: CardStackKey,
    val focusedCardId: LauncherCardId? = null,
)

enum class CardStackNavigationDirection(
    internal val indexDelta: Int,
) {
    PREVIOUS(-1),
    NEXT(1),
}

enum class CardStackFocusRejection {
    DUPLICATE_CARD_IDS,
    UNKNOWN_CARD,
    STALE_SETTLE,
}

sealed interface CardStackFocusResult {
    data class Applied(
        val state: CardStackFocusState,
        val focusChanged: Boolean,
        val boundaryReached: Boolean = false,
    ) : CardStackFocusResult

    data class Rejected(
        val reason: CardStackFocusRejection,
    ) : CardStackFocusResult
}

/**
 * A gesture snapshot deliberately carries the focused identity observed when the gesture began.
 * A settle result is ignored when live content has changed that identity in the meantime.
 */
data class CardStackSettleRequest(
    val focusedCardId: LauncherCardId?,
    val verticalDragPx: Float,
    val verticalVelocityPxPerSecond: Float,
    val distanceThresholdPx: Float,
    val flingVelocityThresholdPxPerSecond: Float,
) {
    init {
        require(distanceThresholdPx >= 0f) { "Settle distance threshold must not be negative." }
        require(flingVelocityThresholdPxPerSecond >= 0f) { "Fling velocity threshold must not be negative." }
    }
}

/**
 * Pure stack-focus policy shared by card surfaces. Gesture and animation layers submit their
 * committed navigation outcome here; transient drag progress intentionally does not belong in
 * [CardStackFocusState].
 */
@Suppress("TooManyFunctions")
class CardStackController {
    fun initialize(
        stackKey: CardStackKey,
        cardIds: List<LauncherCardId>,
    ): CardStackFocusResult =
        cardIds.rejectDuplicateIds()
            ?: CardStackFocusResult.Applied(
                state = CardStackFocusState(stackKey = stackKey, focusedCardId = cardIds.firstOrNull()),
                focusChanged = cardIds.isNotEmpty(),
            )

    /** Restores persisted intent safely, falling back to the first current card when necessary. */
    fun restore(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
    ): CardStackFocusResult =
        cardIds.rejectDuplicateIds()
            ?: apply(state, cardIds.firstOrNull { it == state.focusedCardId } ?: cardIds.firstOrNull())

    fun jumpTo(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        cardId: LauncherCardId,
    ): CardStackFocusResult =
        cardIds.rejectDuplicateIds()
            ?: if (cardId in cardIds) {
                apply(state, cardId)
            } else {
                CardStackFocusResult.Rejected(CardStackFocusRejection.UNKNOWN_CARD)
            }

    /** Moves without cycling. A boundary result lets a surface provide bounded-feedback haptics. */
    fun navigate(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        direction: CardStackNavigationDirection,
    ): CardStackFocusResult =
        cardIds.rejectDuplicateIds()
            ?: navigateValidStack(state, cardIds, direction)

    /**
     * Converts a completed vertical drag or fling into exactly one focus operation. Dragging up
     * moves chronologically forward; dragging down moves back. Insufficient movement is a no-op.
     *
     * The focus captured by [CardStackSettleRequest] is checked before navigation so a delayed
     * result cannot overwrite a focus selected by content reconciliation or another input source.
     */
    fun settle(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        request: CardStackSettleRequest,
    ): CardStackFocusResult =
        cardIds.rejectDuplicateIds()
            ?: settleValidStack(state, cardIds, request)

    private fun settleValidStack(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        request: CardStackSettleRequest,
    ): CardStackFocusResult {
        val motion =
            if (abs(request.verticalVelocityPxPerSecond) >= request.flingVelocityThresholdPxPerSecond) {
                request.verticalVelocityPxPerSecond
            } else {
                request.verticalDragPx
            }
        return when {
            state.focusedCardId != request.focusedCardId ->
                CardStackFocusResult.Rejected(CardStackFocusRejection.STALE_SETTLE)
            abs(motion) < request.distanceThresholdPx -> apply(state, state.focusedCardId)
            else ->
                navigate(
                    state = state,
                    cardIds = cardIds,
                    direction =
                        if (motion < 0f) {
                            CardStackNavigationDirection.NEXT
                        } else {
                            CardStackNavigationDirection.PREVIOUS
                        },
                )
        }
    }

    /**
     * Reconciles a live content update. If focus vanishes, the closest survivor in the prior
     * ordering wins; ties choose the earlier prior card, then the earlier current card. When no
     * prior card survives, focus clears rather than moving to an unrelated replacement card.
     */
    fun reconcile(
        state: CardStackFocusState,
        previousCardIds: List<LauncherCardId>,
        cardIds: List<LauncherCardId>,
    ): CardStackFocusResult =
        previousCardIds.rejectDuplicateIds()
            ?: cardIds.rejectDuplicateIds()
            ?: reconcileValidStacks(state, previousCardIds, cardIds)

    private fun navigateValidStack(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        direction: CardStackNavigationDirection,
    ): CardStackFocusResult {
        val focusedIndex = cardIds.indexOf(state.focusedCardId)
        return when {
            cardIds.isEmpty() -> apply(state, null)
            focusedIndex < 0 -> apply(state, cardIds.first())
            else -> {
                val targetIndex = focusedIndex + direction.indexDelta
                if (targetIndex !in cardIds.indices) {
                    CardStackFocusResult.Applied(
                        state = state.copy(focusedCardId = cardIds[focusedIndex]),
                        focusChanged = state.focusedCardId != cardIds[focusedIndex],
                        boundaryReached = true,
                    )
                } else {
                    apply(state, cardIds[targetIndex])
                }
            }
        }
    }

    private fun reconcileValidStacks(
        state: CardStackFocusState,
        previousCardIds: List<LauncherCardId>,
        cardIds: List<LauncherCardId>,
    ): CardStackFocusResult =
        when {
            state.focusedCardId in cardIds -> apply(state, state.focusedCardId)
            cardIds.isEmpty() -> apply(state, null)
            else -> {
                val previousFocusedIndex = previousCardIds.indexOf(state.focusedCardId)
                apply(
                    state = state,
                    focusedCardId =
                        nearestSurvivor(previousCardIds, cardIds, previousFocusedIndex),
                )
            }
        }

    private fun nearestSurvivor(
        previousCardIds: List<LauncherCardId>,
        cardIds: List<LauncherCardId>,
        previousFocusedIndex: Int,
    ): LauncherCardId? =
        previousFocusedIndex.takeIf { it >= 0 }?.let { focusedIndex ->
            cardIds.withIndex()
                .mapNotNull { current ->
                    previousCardIds.indexOf(current.value)
                        .takeIf { it >= 0 }
                        ?.let { previousIndex -> Triple(current.value, previousIndex, current.index) }
                }.minWithOrNull(
                    compareBy<Triple<LauncherCardId, Int, Int>> { candidate ->
                        abs(candidate.second - focusedIndex)
                    }.thenBy { candidate -> candidate.second }
                        .thenBy { candidate -> candidate.third },
                )?.first
        }

    private fun apply(
        state: CardStackFocusState,
        focusedCardId: LauncherCardId?,
    ): CardStackFocusResult.Applied =
        CardStackFocusResult.Applied(
            state = state.copy(focusedCardId = focusedCardId),
            focusChanged = state.focusedCardId != focusedCardId,
        )

    private fun List<LauncherCardId>.rejectDuplicateIds(): CardStackFocusResult.Rejected? =
        takeIf { ids -> ids.distinct().size != ids.size }
            ?.let { CardStackFocusResult.Rejected(CardStackFocusRejection.DUPLICATE_CARD_IDS) }
}

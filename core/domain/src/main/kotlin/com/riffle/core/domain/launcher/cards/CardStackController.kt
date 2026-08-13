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

    /**
     * Moves [steps] cards without cycling, clamping to whichever boundary card is reached first
     * rather than refusing to move at all -- a hard fling near the end of a short stack should
     * land on the last card, not no-op. A boundary result lets a surface provide bounded-feedback
     * haptics either way.
     */
    fun navigate(
        state: CardStackFocusState,
        cardIds: List<LauncherCardId>,
        direction: CardStackNavigationDirection,
        steps: Int = 1,
    ): CardStackFocusResult {
        require(steps >= 1) { "Steps must be at least 1." }
        return cardIds.rejectDuplicateIds() ?: navigateValidStack(state, cardIds, direction, steps)
    }

    /**
     * Converts a completed vertical drag or fling into one focus operation. Dragging up moves
     * chronologically forward; dragging down moves back. Insufficient movement is a no-op.
     *
     * "Insufficient" is measured as a single combined score: the drag's own distance and the
     * release velocity each expressed as a fraction of their respective thresholds
     * ([CardStackSettleRequest.distanceThresholdPx] and
     * [CardStackSettleRequest.flingVelocityThresholdPxPerSecond]), summed. Anything below 1.0
     * is a no-op; anything at or above 1.0 commits. Neither signal has to clear its own
     * threshold in isolation -- a quick short flick that would fall short of both (say a 40px
     * travel with 300 px/s release velocity against a 64px / 500 px/s pair) still adds to a
     * combined score above 1.0 and commits. Without this, the finger reliably moved the stack
     * visibly during the drag itself (a caller can preview it frame by frame -- see e.g.
     * `adaptiveStageLiveActiveCardIndex`'s doc) and then the release snapped the whole thing
     * back to the origin card, reading as an unresponsive fling.
     *
     * Either a fling (velocity past [CardStackSettleRequest.flingVelocityThresholdPxPerSecond])
     * or a plain drag can skip more than one card: how many is that motion -- the fling's
     * velocity, or the drag's distance -- expressed as a multiple of its own threshold, e.g.
     * twice the distance threshold's worth of drag skips two cards just as twice the velocity
     * threshold's worth of fling does. For a drag, this mirrors the live preview a caller
     * renders while the finger is still down -- a drag that visibly previews several cards
     * flipping past commits that same distance on release instead of springing back to a
     * single-step move.
     *
     * A fling's step count is additionally capped at [MAX_FLING_STEP_COUNT]. Fling velocity has
     * no live preview to anchor a user's expectations to (unlike a drag's own distance), so an
     * uncapped velocity-to-step ratio meant a moderate flick against a low velocity threshold
     * skipped enough cards to cross most of a longer stack and eject to whichever boundary the
     * fling pointed toward -- reading as a jump-cut, not a boosted swipe. The cap keeps every
     * fling in the "a few cards forward" range regardless of how hard the finger was moving.
     * A boundary-adjacent stack still clamps as usual; the cap only prevents a moderate fling
     * from *reaching* that boundary in the first place from mid-stack.
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
        val isFling = abs(request.verticalVelocityPxPerSecond) >= request.flingVelocityThresholdPxPerSecond
        // A single "how intentional was this?" score. Neither drag nor release velocity has to
        // clear its own threshold in isolation -- a quick short flick that would fall short of
        // both (say a 40px travel with 300 px/s release velocity against a 64px / 500 px/s pair)
        // still adds to a combined score above 1 and commits. Without this, the finger reliably
        // moved the stack visibly during the drag itself (adaptiveStageLiveActiveCardIndex
        // renders a fractional preview from any nonzero drag) and then the release snapped the
        // whole thing back to the origin card -- reading as an unresponsive fling.
        val commitEnergy =
            abs(request.verticalDragPx) / request.distanceThresholdPx +
                abs(request.verticalVelocityPxPerSecond) / request.flingVelocityThresholdPxPerSecond
        val steps =
            if (isFling) {
                (abs(request.verticalVelocityPxPerSecond) / request.flingVelocityThresholdPxPerSecond)
                    .toInt()
                    .coerceIn(1, MAX_FLING_STEP_COUNT)
            } else {
                (abs(request.verticalDragPx) / request.distanceThresholdPx)
                    .toInt()
                    .coerceAtLeast(1)
            }
        val directionSignal =
            if (isFling) request.verticalVelocityPxPerSecond else request.verticalDragPx
        return when {
            state.focusedCardId != request.focusedCardId ->
                CardStackFocusResult.Rejected(CardStackFocusRejection.STALE_SETTLE)
            commitEnergy < 1f -> apply(state, state.focusedCardId)
            else ->
                navigate(
                    state = state,
                    cardIds = cardIds,
                    direction =
                        if (directionSignal < 0f) {
                            CardStackNavigationDirection.NEXT
                        } else {
                            CardStackNavigationDirection.PREVIOUS
                        },
                    steps = steps,
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
        steps: Int,
    ): CardStackFocusResult {
        val focusedIndex = cardIds.indexOf(state.focusedCardId)
        return when {
            cardIds.isEmpty() -> apply(state, null)
            focusedIndex < 0 -> apply(state, cardIds.first())
            else -> {
                val rawTargetIndex = focusedIndex + direction.indexDelta * steps
                // For steps == 1 this only clamps at a boundary already sitting on that same
                // index, matching the old single-step no-op exactly. For steps > 1 (a hard
                // fling), it lands on the furthest reachable card instead of refusing to move.
                val targetIndex = rawTargetIndex.coerceIn(cardIds.indices)
                if (rawTargetIndex !in cardIds.indices) {
                    CardStackFocusResult.Applied(
                        state = state.copy(focusedCardId = cardIds[targetIndex]),
                        focusChanged = state.focusedCardId != cardIds[targetIndex],
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

/**
 * The upper bound on how many cards a single fling can skip, regardless of how much its released
 * velocity exceeds the fling velocity threshold. See [CardStackController.settle] for why the
 * cap is fling-specific -- a drag's own step count is still capped only by the stack's own
 * boundary, since the live-preview a caller renders while the finger is still down anchors the
 * user's expectation of how many cards the release will commit.
 */
const val MAX_FLING_STEP_COUNT = 3

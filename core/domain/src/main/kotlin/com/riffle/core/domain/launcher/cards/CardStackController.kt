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
 * Pure stack-focus policy shared by card surfaces. Gesture and animation layers submit their
 * committed navigation outcome here; transient drag progress intentionally does not belong in
 * [CardStackFocusState].
 */
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
        val resolvedIndex = focusedIndex.takeIf { it >= 0 } ?: cardIds.indices.firstOrNull()
        return if (resolvedIndex == null) {
            apply(state, null)
        } else {
            val targetIndex = resolvedIndex + direction.indexDelta
            if (targetIndex !in cardIds.indices) {
                CardStackFocusResult.Applied(
                    state = state.copy(focusedCardId = cardIds[resolvedIndex]),
                    focusChanged = state.focusedCardId != cardIds[resolvedIndex],
                    boundaryReached = true,
                )
            } else {
                apply(state, cardIds[targetIndex])
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

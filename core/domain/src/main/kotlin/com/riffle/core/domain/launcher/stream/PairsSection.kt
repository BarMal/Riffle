package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.pairs.AppPair

/**
 * The pair being made on the Pairs page (ADR 0003, Amendment 7): each half an app or not yet chosen. It lives only
 * while the page is shown and is never stored: it becomes a pair when both halves are chosen, and is dropped when the
 * page is left, the Stream closes or Cancel is pressed.
 */
data class PairDraft(
    val first: AppIdentity? = null,
    val second: AppIdentity? = null,
) {
    /** Both halves are chosen: the draft is saved. */
    val isComplete: Boolean get() = first != null && second != null

    /** [app] chosen for the half [secondHalf]. */
    fun choose(
        app: AppIdentity,
        secondHalf: Boolean,
    ): PairDraft = if (secondHalf) copy(second = app) else copy(first = app)

    /** The app the other half already holds: the picker leaves it out. */
    fun otherHalf(secondHalf: Boolean): AppIdentity? = if (secondHalf) first else second
}

/**
 * The page of the Pairs stack as the Stream draws it, from the stored pairs and the draft. Pure. Front to back:
 * the draft (while there is one), the pairs (most recently opened first), then the "New pair" card (not while a
 * draft is in front: the Cancel chip is how it is left). With no pair the page is the "New pair" card alone.
 */
object PairsSection {
    const val DRAFT_ID = "pair:draft"
    const val NEW_ID = "pair:new"

    fun idOf(pair: AppPair): StreamContentId = StreamContentId("pair:${pair.id}")

    /**
     * The result of the section. [resolve] gives an app its label and icon, or null when it cannot be named (the pair
     * is then left out of the page; a pair of an app that is gone is removed by the uninstall rule).
     */
    fun result(
        pairs: List<AppPair>,
        draft: PairDraft?,
        resolve: (AppIdentity) -> PairSide?,
    ): StreamSectionResult.Ready {
        val saved =
            pairs.mapNotNull { pair ->
                val first = resolve(pair.first)
                val second = resolve(pair.second)
                if (first == null || second == null) null else saved(pair, first, second)
            }
        val front = listOfNotNull(draft?.let { draftCard(it, resolve) })
        val back = listOfNotNull(newCard().takeIf { draft == null })
        val cards = front + saved + back
        return StreamSectionResult.Ready(listOf(StreamGroup(GROUP, null, cards)))
    }

    private fun saved(
        pair: AppPair,
        first: PairSide,
        second: PairSide,
    ) = AppPairContent(
        id = idOf(pair),
        kind = PairCardKind.SAVED,
        pairId = pair.id,
        first = first,
        second = second,
        actions = SAVED_ACTIONS,
        lastOpenedEpochMillis = pair.lastOpenedEpochMillis.takeIf { it > 0L },
    )

    private fun draftCard(
        draft: PairDraft,
        resolve: (AppIdentity) -> PairSide?,
    ) = AppPairContent(
        id = StreamContentId(DRAFT_ID),
        kind = PairCardKind.DRAFT,
        pairId = null,
        first = draft.first?.let(resolve),
        second = draft.second?.let(resolve),
        actions = setOf(PairAction.CHOOSE_FIRST, PairAction.CHOOSE_SECOND),
    )

    private fun newCard() =
        AppPairContent(
            id = StreamContentId(NEW_ID),
            kind = PairCardKind.NEW,
            pairId = null,
            first = null,
            second = null,
            actions = setOf(PairAction.NEW_PAIR),
        )

    private val SAVED_ACTIONS =
        setOf(
            PairAction.OPEN,
            PairAction.SWAP_SIDES,
            PairAction.CHANGE_FIRST,
            PairAction.CHANGE_SECOND,
            PairAction.REMOVE,
        )

    private const val GROUP = "pairs"
}

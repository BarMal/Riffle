package com.riffle.core.domain.launcher.stream

/**
 * Which card of the Stream is open (ADR 0003, Amendment 1, "Stream: browse, then open"), and how it closes. At most one
 * card is open, on the page in front; opening it is a tap on the front card; it closes by a tap on it, a press of the
 * Dock, Back, or by the Stream leaving the page (a switch of page, or the content gone). Pure, so the transitions can
 * be tested without a screen.
 */
data class OpenCardState(
    /** The key of the page the open card is on, or null when no card is open. */
    val pageKey: String? = null,
    /** The id of the open card (the id of the page's entry), or null when no card is open. */
    val cardId: String? = null,
) {
    val isOpen: Boolean get() = cardId != null
}

/** Something that happens to the open card. */
sealed interface OpenCardEvent {
    /** The front card [cardId] of the page [pageKey] was tapped. */
    data class Open(val pageKey: String, val cardId: String) : OpenCardEvent

    /** A tap on the open card (or on the scrim around it). */
    data object CloseByTap : OpenCardEvent

    /** A press of the Dock: the card closes and the Stream stays. */
    data object CloseByPress : OpenCardEvent

    /** System Back: the card closes first. */
    data object CloseByBack : OpenCardEvent

    /** The page in front is now [pageKey]: a card open on another page closes. */
    data class PageChanged(val pageKey: String?) : OpenCardEvent

    /** The cards the open page has now: an open card that is no longer among them closes. */
    data class CardsChanged(val pageKey: String, val cardIds: Set<String>) : OpenCardEvent

    /** The Stream left the screen. */
    data object StreamLeft : OpenCardEvent
}

object OpenCardReducer {
    fun reduce(
        state: OpenCardState,
        event: OpenCardEvent,
    ): OpenCardState =
        when (event) {
            is OpenCardEvent.Open -> OpenCardState(event.pageKey, event.cardId)
            OpenCardEvent.CloseByTap, OpenCardEvent.CloseByPress, OpenCardEvent.CloseByBack,
            OpenCardEvent.StreamLeft,
            -> OpenCardState()
            is OpenCardEvent.PageChanged ->
                if (state.pageKey == event.pageKey) state else OpenCardState()
            is OpenCardEvent.CardsChanged ->
                if (state.pageKey == event.pageKey && state.cardId !in event.cardIds) OpenCardState() else state
        }

    /**
     * Whether a press of the Dock, or Back, is taken by the open card: while a card is open it closes first, and the
     * Stream (the press's usual work) stays.
     */
    fun takesPressOrBack(state: OpenCardState): Boolean = state.isOpen
}

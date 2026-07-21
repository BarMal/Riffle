package com.riffle.core.domain.launcher.cards

data class CardExpansionState(
    val phase: CardExpansionPhase = CardExpansionPhase.COLLAPSED,
    val cardId: LauncherCardId? = null,
) {
    init {
        require((phase == CardExpansionPhase.COLLAPSED) == (cardId == null)) {
            "Only a collapsed card expansion state may omit a card id."
        }
    }

    fun expand(
        cardId: LauncherCardId,
        reducedMotion: Boolean = false,
    ): CardExpansionState =
        CardExpansionState(
            phase = if (reducedMotion) CardExpansionPhase.EXPANDED else CardExpansionPhase.EXPANDING,
            cardId = cardId,
        )

    /**
     * Reconciles transient expansion against the live cards rendered by its owner.
     *
     * Detail payloads are deliberately process-only, so removal must close the detail rather
     * than retaining an identity that can no longer be acted on.
     */
    fun reconcile(
        availableCardIds: Set<LauncherCardId>,
        reducedMotion: Boolean = false,
    ): CardExpansionState =
        cardId
            ?.takeIf { it !in availableCardIds }
            ?.let { collapse(reducedMotion).complete() }
            ?: this

    fun collapse(reducedMotion: Boolean = false): CardExpansionState =
        when (phase) {
            CardExpansionPhase.COLLAPSED -> this
            CardExpansionPhase.EXPANDING,
            CardExpansionPhase.EXPANDED,
            -> if (reducedMotion) CardExpansionState() else copy(phase = CardExpansionPhase.COLLAPSING)
            CardExpansionPhase.COLLAPSING -> this
        }

    fun complete(): CardExpansionState =
        when (phase) {
            CardExpansionPhase.COLLAPSED,
            CardExpansionPhase.EXPANDED,
            -> this
            CardExpansionPhase.EXPANDING -> copy(phase = CardExpansionPhase.EXPANDED)
            CardExpansionPhase.COLLAPSING -> CardExpansionState()
        }

    val isVisible: Boolean
        get() = phase != CardExpansionPhase.COLLAPSED
}

enum class CardExpansionPhase {
    COLLAPSED,
    EXPANDING,
    EXPANDED,
    COLLAPSING,
}

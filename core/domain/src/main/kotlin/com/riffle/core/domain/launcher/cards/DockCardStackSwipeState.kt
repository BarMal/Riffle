package com.riffle.core.domain.launcher.cards

enum class DockCardStackContent {
    APPS,
    NOTIFICATIONS,
}

enum class DockCardStackSwipeDirection(
    internal val indexDelta: Int,
) {
    NEXT(1),
    PREVIOUS(-1),
}

data class DockCardStackSwipeState(
    val content: DockCardStackContent,
    val direction: DockCardStackSwipeDirection,
    val outgoingCardIndex: Int,
    val incomingCardIndex: Int,
    val newBackCardIndex: Int?,
) {
    init {
        require(outgoingCardIndex >= 0) { "Outgoing card index must not be negative." }
        require(incomingCardIndex >= 0) { "Incoming card index must not be negative." }
        require(outgoingCardIndex != incomingCardIndex) { "Outgoing and incoming cards must differ." }
        require(newBackCardIndex == null || newBackCardIndex >= 0) { "Back card index must not be negative." }
        require(newBackCardIndex !in setOf(outgoingCardIndex, incomingCardIndex)) {
            "Back card must differ from outgoing and incoming cards."
        }
    }

    companion object {
        fun create(
            cardCount: Int,
            activeCardIndex: Int,
            direction: DockCardStackSwipeDirection,
            content: DockCardStackContent,
        ): DockCardStackSwipeState? {
            require(cardCount >= 0) { "Card count must not be negative." }
            if (cardCount == 0) return null
            require(activeCardIndex in 0 until cardCount) { "Active card index must be in the stack." }

            val incomingCardIndex = activeCardIndex + direction.indexDelta
            return incomingCardIndex.takeIf { it in 0 until cardCount }?.let { incoming ->
                DockCardStackSwipeState(
                    content = content,
                    direction = direction,
                    outgoingCardIndex = activeCardIndex,
                    incomingCardIndex = incoming,
                    newBackCardIndex = (incoming + direction.indexDelta).takeIf { it in 0 until cardCount },
                )
            }
        }
    }
}

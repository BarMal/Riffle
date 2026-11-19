package com.riffle.core.domain.launcher.stream

/** One card of a stack as the follow rule sees it: its id (stable while content changes) and what its going means. */
data class StackCardRef(
    val id: String,
    /**
     * Whether this card going away from the stack means it has been played: a queue card that left the queue (ADR 0003,
     * Amendment 18). The person who was on it is then on the front, where what plays now is.
     */
    val goneMeansPlayed: Boolean = false,
)

/**
 * Where a stack's position goes when its cards change under it (ADR 0003, Amendment 18): the person stays on the SAME
 * card, followed by its id, not on the same index. A media stack's queue moves up as the track advances (the card of
 * the track that was next becomes the playing one, the rest shift by one), and the index the person was on would then
 * be another track; the id is what they were looking at. Pure and never throws.
 */
object StackFollow {
    /**
     * The index in [now] (card ids, in order) of the card that stood at [previousIndex] in [previous]:
     * - the same card, wherever it is now;
     * - a card that [StackCardRef.goneMeansPlayed] has gone: the front (index 0), where what plays now stands;
     * - any other card that went away: the card that followed it and is still there, else the one before it, else the
     *   front (a card removed from a pile leaves the next one in its place).
     * An index out of range is first held to [previous]; an empty [now] is index 0.
     */
    fun indexAfterChange(
        previous: List<StackCardRef>,
        previousIndex: Int,
        now: List<String>,
    ): Int {
        if (now.isEmpty() || previous.isEmpty()) return 0
        val index = previousIndex.coerceIn(0, previous.lastIndex)
        val card = previous[index]
        val same = now.indexOf(card.id)
        return when {
            same >= 0 -> same
            card.goneMeansPlayed -> 0
            else -> survivorNear(previous, index, now)
        }
    }

    private fun survivorNear(
        previous: List<StackCardRef>,
        index: Int,
        now: List<String>,
    ): Int {
        fun position(at: Int): Int? = now.indexOf(previous[at].id).takeIf { it >= 0 }
        val after = (index + 1..previous.lastIndex).firstNotNullOfOrNull(::position)
        val before = (index - 1 downTo 0).firstNotNullOfOrNull(::position)
        return after ?: before ?: 0
    }
}

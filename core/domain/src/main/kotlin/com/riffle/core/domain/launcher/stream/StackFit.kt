package com.riffle.core.domain.launcher.stream

/**
 * Makes a stack fit the room it stands in (device fixes, round 7, item 1): the unfolded column is as tall as the
 * Dock's body, and a stack taller than it (a media card 303 dp tall, three strips of 64 dp, and the quick actions strip
 * under them in a body 475 dp tall) started above the Dock's top edge. A stack that does not fit gives up, in this
 * order: height of the cards that can give it (the front card; a media card's artwork, a notification's lines), down to
 * each card's floor; then strips, one at a time, until what is left fits. Pure, so the rule is tested without a screen.
 */
object StackFit {
    /** The stack as it is drawn: how many strips peek above the front card, and how tall each card is. */
    data class Result(
        val peeks: Int,
        val heightsDp: List<Float>,
    )

    /**
     * [heightsDp] are the cards' natural heights, newest (front at rest) first; [floorsDp] the least each can be drawn
     * at (never more than its height; a card that cannot give any height has a floor equal to it). At most
     * [wantedPeeks] strips of [pitchDp] stand above a card. [areaDp] is the room: the stack's top is its front card's
     * height plus its strips above the area's bottom, and must not be above the area's top.
     *
     * Every card, as the front card in its turn, fits: its height is at most [areaDp] less its strips (the cards
     * behind it, as many as there are, at most the returned peeks). Peeks are the most that leave every card at least
     * its floor.
     */
    fun fit(
        heightsDp: List<Float>,
        floorsDp: List<Float>,
        pitchDp: Float,
        wantedPeeks: Int,
        areaDp: Float,
    ): Result {
        if (heightsDp.isEmpty() || areaDp <= 0f) return Result(wantedPeeks.coerceAtLeast(0), heightsDp)
        val count = heightsDp.size
        var peeks = wantedPeeks.coerceIn(0, (count - 1).coerceAtLeast(0))

        fun stripsOf(index: Int) = minOf(count - 1 - index, peeks)

        fun fits(floor: (Int) -> Float): Boolean =
            heightsDp.indices.all { floor(it) + stripsOf(it) * pitchDp <= areaDp }

        val floors = heightsDp.indices.map { minOf(floorsDp.getOrElse(it) { heightsDp[it] }, heightsDp[it]) }
        while (peeks > 0 && !fits { floors[it] }) peeks--
        val capped =
            heightsDp.indices.map { index ->
                val cap = (areaDp - stripsOf(index) * pitchDp).coerceAtLeast(0f)
                // Held to the room, never below the card's floor unless even that does not fit.
                heightsDp[index].coerceAtMost(cap).coerceAtLeast(floors[index].coerceAtMost(cap))
            }
        return Result(peeks, capped)
    }
}

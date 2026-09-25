package com.riffle.core.domain.launcher.cards

/**
 * One drag step applied to a card stack's live scroll position.
 *
 * @param position the new position, held inside the stack's reachable range.
 * @param consumedDelta how much of the step's own delta the stack actually used. What is left over
 *   (`delta - consumedDelta`) is handed to ancestors through nested scroll, which is how a swipe
 *   past the first or last card reaches the home gesture layer instead of dying in the stack.
 * @param pinned whether the step pushed against an end of the range.
 */
data class CardStackScrollStep(
    val position: Float,
    val consumedDelta: Float,
    val pinned: Boolean,
)

/**
 * Applies [delta] (plus [slopCredit], the touch slop `Modifier.scrollable` withheld before its first
 * report, which moves the position but is not part of this step's delta) to [position].
 *
 * With a [range] the position is clamped to it and only the part of [delta] that moved the position
 * counts as consumed; the consumed amount always has [delta]'s sign and never exceeds it. Without a
 * range (a stack that does not scroll continuously) the whole delta is consumed, as before.
 */
fun cardStackScrollStep(
    position: Float,
    delta: Float,
    range: ClosedFloatingPointRange<Float>?,
    slopCredit: Float = 0f,
): CardStackScrollStep {
    val unclamped = position + slopCredit + delta
    if (range == null) return CardStackScrollStep(unclamped, delta, pinned = false)
    val clamped = unclamped.coerceIn(range)
    val overflow = unclamped - clamped
    val consumed =
        if (delta >= 0f) {
            (delta - overflow).coerceIn(0f, delta)
        } else {
            (delta - overflow).coerceIn(delta, 0f)
        }
    return CardStackScrollStep(position = clamped, consumedDelta = consumed, pinned = overflow != 0f)
}

/**
 * Whether a stack of [cardCount] cards should own drags along its axis at all. A stack with one
 * card (or none) has nowhere to go, so it leaves the touch entirely to its ancestors -- the home
 * layer then sees an ordinary, unconsumed swipe.
 */
fun cardStackOwnsDrag(cardCount: Int): Boolean = cardCount > 1

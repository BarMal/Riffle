package com.riffle.core.domain.launcher.stream

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Dragging from one open card to the next in the same stack (ADR 0003, Amendment 1, "Browse, then open"; device fixes,
 * round 5). A vertical drag that starts on a strip, on the open card's pinned header or actions or on the room beside
 * the card moves the stack at once, with the stack's own drag, fling and magnet. A drag that starts in the card's
 * scrolling body scrolls the body first (platform nested scrolling); only what the body cannot take, at its top or its
 * bottom, is handed to the stack, and only once it has gone [OpenCardHandoff.THRESHOLD_DP] past the edge. Pure, so the
 * rules are tested without a screen.
 */

/**
 * The hand-off from an open card's scrolling body to the stack. The body consumes a drag first; the part it cannot
 * take (it is at its top or bottom) is [leftover], accumulated. Below [THRESHOLD_DP] the stack does not move at all, so
 * a short overscroll changes nothing and a card is never flipped by accident; past it the stack follows the finger
 * by what exceeds the threshold, one to one, and a finger that comes back takes the stack back first ([retract]) before
 * the body scrolls again. Lifting the finger ends the hand-off ([reset]).
 */
class OpenCardHandoff(private val thresholdDp: Float = THRESHOLD_DP) {
    /** What the body could not take, summed, in dp of finger travel (positive: down). */
    var accumulated: Float = 0f
        private set

    /** How much of [accumulated] the stack has been moved by: what exceeds the threshold, with the sign. */
    var moved: Float = 0f
        private set

    /** Whether the finger is out past the body's edge (anything accumulated). */
    val active: Boolean get() = accumulated != 0f

    /**
     * The body left [deltaDp] of the finger's travel (positive: down) unused. Returns how far the stack should move by
     * now, in dp of finger travel: 0 until the threshold is passed.
     */
    fun leftover(deltaDp: Float): Float {
        accumulated += deltaDp
        return follow()
    }

    /** What a finger's travel [deltaDp] is split into when it comes back toward where the body's edge was reached. */
    data class Retraction(val consumedDp: Float, val stackDp: Float)

    /**
     * Before the body scrolls: a travel [deltaDp] against what has accumulated first undoes it (up to all of it), so
     * the stack gives the way back before the body moves. Nothing is consumed with nothing accumulated or when the
     * finger goes on the same way.
     */
    fun retract(deltaDp: Float): Retraction {
        val opposes = accumulated != 0f && deltaDp != 0f && (accumulated > 0f) != (deltaDp > 0f)
        if (!opposes) return Retraction(0f, 0f)
        val used = if (deltaDp > 0f) min(deltaDp, -accumulated) else max(deltaDp, -accumulated)
        accumulated += used
        return Retraction(used, follow())
    }

    /** The finger lifted: the next drag starts afresh. */
    fun reset() {
        accumulated = 0f
        moved = 0f
    }

    private fun follow(): Float {
        val beyond = max(0f, abs(accumulated) - thresholdDp)
        val target = if (accumulated < 0f) -beyond else beyond
        val step = target - moved
        moved = target
        return step
    }

    companion object {
        /** How far past the body's edge the finger must go before the stack starts to move: a deliberate drag. */
        const val THRESHOLD_DP = 56f
    }
}

/**
 * While the stack moves from one card to the next with a card open, each card is drawn between how its slot in the
 * stack looks and how it looks open, by how near it is to the front: the one coming to the front grows into its open
 * card as the one leaving shrinks back to its strip. One continuous motion of the stack's position.
 */
object OpenCardBlend {
    /**
     * How open the card [r] places from the front (`index - position`) is, 0 to 1: whole at the front, nothing a card
     * away.
     */
    fun openness(r: Float): Float = (1f - abs(r)).coerceIn(0f, 1f)

    /** [from] to [to] by [weight] (0 to 1). */
    fun lerp(
        from: Float,
        to: Float,
        weight: Float,
    ): Float = from + (to - from) * weight

    /**
     * Where the open frame's top edge is at [position]: the tops of the two cards the position lies between, blended
     * by how far along it is ([topOf] gives a card's top when it is the open one; a card that does not open stands at
     * its browse top, so the stack settles back as it moves to it).
     */
    fun followTop(
        position: Float,
        topOf: (Int) -> Float,
    ): Float {
        val low = kotlin.math.floor(position).toInt()
        val fraction = position - low
        return if (fraction <= 0f) topOf(low) else lerp(topOf(low), topOf(low + 1), fraction)
    }
}

package com.riffle.core.domain.launcher.stream

import kotlin.math.sqrt

/**
 * How the app grid fades beneath the Stream's column (ADR 0003, Amendment 2: Home is one continuous grid that fades
 * out softly towards the cards and is never cut off). Each item reads its own opacity from where it is on screen, so
 * no edge is drawn and nothing is cut. The fade is a depth cue, not a switch: it is one smooth curve of the signed
 * distance from the column's edge (positive outside, negative inside), from untouched [FEATHER_DP] clear of the column
 * to [PEEK_ALPHA] (apps peeking through) or nothing (peek-through off) [INNER_DP] inside it. It starts well outside the
 * column and goes on inside it, so neighbouring columns of apps never step from full strength to the floor: an app that
 * is one column away is a little dimmer, one at the edge dimmer still, and only the ones well under the cards are at
 * the floor. Pure, so it is tested; the UI only calls it per item.
 */
object StreamGridFade {
    /** How far outside the column's edge the grid starts to fade, in dp. */
    const val FEATHER_DP = 240f

    /** How far inside the column's edge the fade goes on to reach its floor, in dp. */
    const val INNER_DP = 64f

    /** What an item under the column keeps with apps peeking through: dim, but still an app and not a greyed-out one. */
    const val PEEK_ALPHA = 0.30f

    /** The opacity under the column: [PEEK_ALPHA], or 0 with peek-through off. */
    fun floorAlpha(peekThrough: Boolean): Float = if (peekThrough) PEEK_ALPHA else 0f

    /**
     * The opacity of an item centred at ([xDp], [yDp]) with [column] standing and [peekThrough] chosen. [presence]
     * runs from 0 (no column: the grid untouched) to 1 (the column in place), so the fade comes and goes with it.
     * [open] runs from 0 to 1 as a card opens: apps that peek through the resting stack go out behind an open card,
     * which is dense text. A null or empty column fades nothing.
     */
    fun alpha(
        xDp: Float,
        yDp: Float,
        column: StreamColumnRect?,
        peekThrough: Boolean,
        presence: Float = 1f,
        open: Float = 0f,
    ): Float {
        if (column == null || column.isEmpty) return 1f
        val distance = signedDistance(xDp, yDp, column)
        val t = smoothStep((distance + INNER_DP) / (FEATHER_DP + INNER_DP))
        val floor = floorAlpha(peekThrough) * (1f - open.coerceIn(0f, 1f))
        val faded = floor + t * (1f - floor)
        return 1f - presence.coerceIn(0f, 1f) * (1f - faded)
    }

    /** The distance from ([xDp], [yDp]) to [column]'s edge: positive outside it, negative (the depth) inside it. */
    private fun signedDistance(
        xDp: Float,
        yDp: Float,
        column: StreamColumnRect,
    ): Float {
        val dx = maxOf(column.left - xDp, 0f, xDp - column.right)
        val dy = maxOf(column.top - yDp, 0f, yDp - column.bottom)
        if (dx > 0f || dy > 0f) return sqrt(dx * dx + dy * dy)
        val depth = minOf(xDp - column.left, column.right - xDp, yDp - column.top, column.bottom - yDp)
        return -depth
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

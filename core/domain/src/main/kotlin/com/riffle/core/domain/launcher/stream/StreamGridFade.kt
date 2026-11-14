package com.riffle.core.domain.launcher.stream

import kotlin.math.sqrt

/**
 * How the app grid fades beneath the Stream's column (ADR 0003, Amendment 2: Home is one continuous grid that fades
 * out softly towards the cards and is never cut off). Each item reads its own opacity from where it is on screen, so
 * no edge is drawn and nothing is cut: an item well clear of the column is untouched, one under it is [PEEK_ALPHA]
 * (apps peeking through) or gone (peek-through off), and between the two the opacity eases over [FEATHER_DP], the
 * distance from the column's edge to the middle of the item. Pure, so it is tested; the UI only calls it per item.
 */
object StreamGridFade {
    /** How far from the column's edge the grid starts to fade, in dp. */
    const val FEATHER_DP = 144f

    /** What an item under the column keeps with apps peeking through. */
    const val PEEK_ALPHA = 0.22f

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
        val dx = maxOf(column.left - xDp, 0f, xDp - column.right)
        val dy = maxOf(column.top - yDp, 0f, yDp - column.bottom)
        val distance = sqrt(dx * dx + dy * dy)
        val t = 1f - smoothStep(distance / FEATHER_DP)
        val floor = floorAlpha(peekThrough) * (1f - open.coerceIn(0f, 1f))
        val faded = 1f - t * (1f - floor)
        return 1f - presence.coerceIn(0f, 1f) * (1f - faded)
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

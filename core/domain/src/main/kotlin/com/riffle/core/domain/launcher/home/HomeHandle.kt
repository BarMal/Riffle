package com.riffle.core.domain.launcher.home

import kotlin.math.roundToInt

/**
 * The Home handle (ADR 0003, Amendment 1): the page dots and the search entry as one small pill just above the Dock,
 * toward Home. This is its pure part: its sizes, the quick-drag along the dots (a position on the track is a page, and
 * the other way round), and the step to the previous or next page that the handle's accessibility actions take.
 */
object HomeHandle {
    /** The least a touch target is, however small the pill draws. */
    const val TOUCH_TARGET_DP: Int = 48

    /** How tall the pill is drawn. */
    const val PILL_HEIGHT_DP: Int = 36

    /** The width of the glyph that says the handle searches; it sits centred in a [TOUCH_TARGET_DP] square. */
    const val SEARCH_GLYPH_DP: Int = 18

    /** The room after the last dot. */
    const val TRAILING_PADDING_DP: Int = 14

    const val DOT_DIAMETER_DP: Int = 6
    const val DOT_SPACING_DP: Int = 6

    /** The width of the dots' track: the row the quick-drag runs along. */
    fun trackWidthDp(pageCount: Int): Int =
        if (pageCount <= 0) 0 else pageCount * DOT_DIAMETER_DP + (pageCount - 1) * DOT_SPACING_DP

    /**
     * The width of the pill: the glyph's square and, with more than one page, the dots and the room after them. A
     * single page has no dots to show, so the pill is just the square.
     */
    fun pillWidthDp(pageCount: Int): Int =
        TOUCH_TARGET_DP + if (showsDots(pageCount)) trackWidthDp(pageCount) + TRAILING_PADDING_DP else 0

    /** The width of the touch target: the pill's, which is never less than [TOUCH_TARGET_DP]. */
    fun touchWidthDp(pageCount: Int): Int = pillWidthDp(pageCount)

    /** Whether the dots, and so the quick-drag, are there. */
    fun showsDots(pageCount: Int): Boolean = pageCount > 1

    /**
     * The page under a finger [dragPositionPx] along a track [trackWidthPx] wide. The track's left is its physical
     * left; [rtl] reads the pages from the right. Past either end it is the last or first page.
     */
    fun pageAt(
        dragPositionPx: Float,
        trackWidthPx: Float,
        pageCount: Int,
        rtl: Boolean = false,
    ): Int {
        if (pageCount <= 1 || trackWidthPx <= 0f) return 0
        val progress = dragPositionPx / trackWidthPx
        val logical = if (rtl) 1f - progress else progress
        return (logical * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
    }

    /** The exact inverse of [pageAt]: where a page's dot sits along the track, in px from its physical left. */
    fun offsetOf(
        page: Int,
        trackWidthPx: Float,
        pageCount: Int,
        rtl: Boolean = false,
    ): Float {
        if (pageCount <= 1) return 0f
        val progress = page.toFloat() / (pageCount - 1)
        val track = if (rtl) 1f - progress else progress
        return (track * trackWidthPx).coerceIn(0f, trackWidthPx)
    }

    /** The page [delta] steps from [selected], or null when that is past either end (nothing to do). */
    fun step(
        selected: Int,
        pageCount: Int,
        delta: Int,
    ): Int? {
        if (pageCount <= 1) return null
        val target = selected + delta
        return target.takeIf { it in 0 until pageCount && it != selected }
    }
}

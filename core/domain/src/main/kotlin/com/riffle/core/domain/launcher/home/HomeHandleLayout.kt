package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.settings.PageScrollDirection

/** The way the handle runs: along a row ([HORIZONTAL]) or down a column ([VERTICAL]). */
enum class HandleAxis {
    HORIZONTAL,
    VERTICAL,
}

/** The axis the handle runs along for [this] way of scrolling the pages: it always follows the pages. */
val PageScrollDirection.handleAxis: HandleAxis
    get() =
        when (this) {
            PageScrollDirection.HORIZONTAL -> HandleAxis.HORIZONTAL
            PageScrollDirection.VERTICAL -> HandleAxis.VERTICAL
        }

/** Where, around the pages, the handle stands (physical sides, as Dock edges are). */
enum class HandleStrip {
    /** A row below the pages: above a bottom Dock (and, as it always was, below the pages for a top Dock). */
    BOTTOM_ROW,

    /** A column between a left Dock and the pages, vertically centred: in front of the Dock's middle. */
    LEFT_COLUMN,

    /** A column between the pages and a right Dock, vertically centred: in front of the Dock's middle. */
    RIGHT_COLUMN,
}

/** The pill's drawn size, in dp. */
data class HandleSize(
    val widthDp: Int,
    val heightDp: Int,
)

/**
 * Where the Home handle stands and how big it is for a Dock edge and a way of scrolling the pages (ADR 0003, Amendment
 * 2). The pill's own sizes and the quick-drag mapping are [HomeHandle]'s; this is the placement around the pages.
 */
object HomeHandleLayout {
    /**
     * The strip the handle stands in for a Dock on [dockEdge], the pill running along [axis]: just in from the Dock, on
     * Home's side of it. A column beside a left or right Dock is along the Dock, so it stands in a column of its own in
     * front of the Dock's middle. A row stands in the row below the pages, above a bottom Dock; a row beside a side
     * Dock runs across the Dock's edge, and a strip as wide as the row would squeeze the pages (and clip their labels),
     * so it keeps the row below the pages, where it always was.
     */
    fun stripFor(
        dockEdge: DockPosition,
        axis: HandleAxis,
    ): HandleStrip =
        when {
            axis != HandleAxis.VERTICAL -> HandleStrip.BOTTOM_ROW
            dockEdge == DockPosition.LEFT -> HandleStrip.LEFT_COLUMN
            dockEdge == DockPosition.RIGHT -> HandleStrip.RIGHT_COLUMN
            else -> HandleStrip.BOTTOM_ROW
        }

    /**
     * The pill's length along [axis]: the glyph's square, then (while there are dots) the dots and the room after them.
     * [showsDots] is false for a single page and when the indicator is off.
     */
    fun lengthDp(
        pageCount: Int,
        showsDots: Boolean = HomeHandle.showsDots(pageCount),
    ): Int =
        HomeHandle.TOUCH_TARGET_DP +
            if (showsDots && HomeHandle.showsDots(pageCount)) {
                HomeHandle.trackWidthDp(pageCount) + HomeHandle.TRAILING_PADDING_DP
            } else {
                0
            }

    /** The pill's size on [axis]: [HomeHandle.TOUCH_TARGET_DP] across, [lengthDp] along. */
    fun sizeDp(
        axis: HandleAxis,
        pageCount: Int,
        showsDots: Boolean = HomeHandle.showsDots(pageCount),
    ): HandleSize {
        val length = lengthDp(pageCount, showsDots)
        return when (axis) {
            HandleAxis.HORIZONTAL -> HandleSize(widthDp = length, heightDp = HomeHandle.TOUCH_TARGET_DP)
            HandleAxis.VERTICAL -> HandleSize(widthDp = HomeHandle.TOUCH_TARGET_DP, heightDp = length)
        }
    }

    /**
     * The room the handle takes across [dockEdge], in dp: its height in the row below the pages, its width in the
     * column beside a side Dock. The pages give it up, so the handle never covers an icon.
     */
    fun reservedDp(
        dockEdge: DockPosition,
        axis: HandleAxis,
        pageCount: Int,
        showsDots: Boolean = HomeHandle.showsDots(pageCount),
    ): Int {
        val size = sizeDp(axis, pageCount, showsDots)
        return if (stripFor(dockEdge, axis) == HandleStrip.BOTTOM_ROW) size.heightDp else size.widthDp
    }

    /** The coordinate of a point that lies along [axis]: x for a row, y for a column. */
    fun alongAxis(
        axis: HandleAxis,
        x: Float,
        y: Float,
    ): Float = if (axis == HandleAxis.VERTICAL) y else x

    /**
     * The page under a finger at ([x], [y]) on a track [trackLengthPx] long running along [axis]. A column reads from
     * the top (page 0 is the top dot); a row reads from the left, or from the right with [rtl].
     */
    fun pageAtPoint(
        axis: HandleAxis,
        x: Float,
        y: Float,
        trackLengthPx: Float,
        pageCount: Int,
        rtl: Boolean = false,
    ): Int =
        HomeHandle.pageAt(
            dragPositionPx = alongAxis(axis, x, y),
            trackWidthPx = trackLengthPx,
            pageCount = pageCount,
            rtl = rtl && axis == HandleAxis.HORIZONTAL,
        )
}

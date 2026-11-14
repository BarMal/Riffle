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
     * The strip the handle stands in for a Dock on [dockEdge]: beside a left or right Dock always the column in front
     * of the Dock's middle, on Home's side of it, whichever way the pages scroll; a row above a bottom Dock (and, as it
     * always was, below the pages for a top Dock).
     */
    fun stripFor(dockEdge: DockPosition): HandleStrip =
        when (dockEdge) {
            DockPosition.LEFT -> HandleStrip.LEFT_COLUMN
            DockPosition.RIGHT -> HandleStrip.RIGHT_COLUMN
            DockPosition.TOP,
            DockPosition.BOTTOM,
            -> HandleStrip.BOTTOM_ROW
        }

    /**
     * The axis the pill runs along for a Dock on [dockEdge] and pages that scroll [pageScroll]: along a side Dock
     * (down the column, glyph first and the dots stacked) whichever way the pages scroll, and along the pages above a
     * bottom Dock. A drag along it scrubs the pages in page order either way.
     */
    fun axisFor(
        dockEdge: DockPosition,
        pageScroll: PageScrollDirection,
    ): HandleAxis = if (dockEdge.isHorizontalEdge) pageScroll.handleAxis else HandleAxis.VERTICAL

    /**
     * The pages' margin on the Dock's side once the handle's column stands in it: the handle takes its room from the
     * margin first, so the grid gives up only what the margin could not hold ([reservedDp] past [marginDp]).
     */
    fun remainingMarginDp(
        marginDp: Int,
        reservedDp: Int,
    ): Int = (marginDp - reservedDp).coerceAtLeast(0)

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
        return if (stripFor(dockEdge) == HandleStrip.BOTTOM_ROW) size.heightDp else size.widthDp
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

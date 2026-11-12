package com.riffle.core.domain.launcher.split

import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.stream.StreamSplit
import kotlin.math.abs

/** How the two panes sit: next to each other, or (a tabletop's horizontal hinge) one over the other. */
enum class SplitOrientation {
    SIDE_BY_SIDE,
    STACKED,
}

/** The end of the split axis the Stream's pane is at: the left (or top) end, or the right (or bottom) end. */
enum class StreamPaneSide {
    START,
    END,
}

/** An axis-aligned rectangle in window dp. */
data class PaneRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    companion object {
        val EMPTY = PaneRect(0f, 0f, 0f, 0f)
    }
}

/**
 * Everything the pane math reads, in dp in window coordinates: the window, the system insets, the Dock (its edge and
 * the room it reserves across that edge), where the Dock's dynamic side lies, and any separating hinges.
 */
data class SplitWindow(
    val widthDp: Float,
    val heightDp: Float,
    val dockEdge: DockPosition = DockPosition.BOTTOM,
    val dockExtentDp: Float = 0f,
    val safeLeftDp: Float = 0f,
    val safeTopDp: Float = 0f,
    val safeRightDp: Float = 0f,
    val safeBottomDp: Float = 0f,
    val hinges: List<AdaptiveStageHingeBounds> = emptyList(),
    /** For a top or bottom Dock: whether its dynamic side is the physically right-hand one. Unknown reads as right. */
    val dynamicSideAtRight: Boolean = true,
)

/** The room the divider can move in, along the split axis, and how far it may go. */
data class SplitLimits(
    val regionStartDp: Float,
    val regionEndDp: Float,
    /** The Stream's smallest and largest share of the region in a split. */
    val minShare: Float,
    val maxShare: Float,
    /** False where the region is too small for two usable panes: only Home only and Stream only then. */
    val splitAvailable: Boolean,
) {
    val lengthDp: Float get() = (regionEndDp - regionStartDp).coerceAtLeast(0f)
}

/** The panes for one [mode]; rectangles in window dp. */
data class SplitLayout(
    val mode: SplitMode,
    val orientation: SplitOrientation,
    val streamSide: StreamPaneSide,
    /** Home's surface: when not against the Dock's edge it reaches over the Dock's room, which it pads itself. */
    val home: PaneRect,
    val stream: PaneRect,
    /** The divider's handle span and its centre along the axis, or null outside a split. */
    val divider: PaneRect?,
    /** Whether the divider is held on the hinge. */
    val snappedToHinge: Boolean,
    /** The share the layout was drawn at (after any hinge snap). */
    val share: Float,
)

/**
 * The pure pane math of the unfolded split (ADR 0003, Folded and unfolded). The Stream's *share* runs 0 (Home only)
 * to 1 (Stream only) of the region left once the Dock's room and the system insets are taken off the split axis; the
 * panes themselves reach out to the window's edges so glass and wallpaper are not cut short.
 *
 * The Stream's surface stops at the Dock's room (Home's pads for it), so what it draws is the same from a split to
 * Stream only.
 *
 * Which side the Stream takes: for a bottom or top Dock, the side nearer the Dock's dynamic side (the Stream button is
 * there), so the pull and the pane are one gesture; for a side Dock, the side away from the Dock (Home keeps the Dock's
 * edge); in a stacked (tabletop) split, the pane nearer the Dock for a bottom or top Dock, and the lower pane for a
 * side Dock. Never across a hinge: a pane that would cover a hinge is cut back to the larger side of it.
 */
@Suppress("TooManyFunctions")
object SplitGeometry {
    /** The divider's own thickness between two panes. */
    const val DIVIDER_GAP_DP = 16f

    /** The divider is held on the hinge when within this distance of it. */
    const val HINGE_SNAP_DP = 32f

    /** Each pane keeps at least this much room along the axis. */
    const val MIN_PANE_DP = 280f

    /** The divider's touch target across the axis. */
    const val DIVIDER_TARGET_DP = 48f

    fun orientationFor(hinges: List<AdaptiveStageHingeBounds>): SplitOrientation =
        if (hinges.none { it.isVertical } && hinges.any { !it.isVertical }) {
            SplitOrientation.STACKED
        } else {
            SplitOrientation.SIDE_BY_SIDE
        }

    fun streamSideFor(window: SplitWindow): StreamPaneSide = streamSideFor(window, orientationFor(window.hinges))

    fun streamSideFor(
        window: SplitWindow,
        orientation: SplitOrientation,
    ): StreamPaneSide =
        when (orientation) {
            SplitOrientation.SIDE_BY_SIDE ->
                when (window.dockEdge) {
                    DockPosition.BOTTOM, DockPosition.TOP ->
                        if (window.dynamicSideAtRight) StreamPaneSide.END else StreamPaneSide.START
                    DockPosition.LEFT -> StreamPaneSide.END
                    DockPosition.RIGHT -> StreamPaneSide.START
                }
            SplitOrientation.STACKED ->
                if (window.dockEdge == DockPosition.TOP) StreamPaneSide.START else StreamPaneSide.END
        }

    /** The hinge across the split axis, as its extent along that axis, or null. */
    fun hingeSpan(
        window: SplitWindow,
        orientation: SplitOrientation,
    ): Pair<Float, Float>? =
        when (orientation) {
            SplitOrientation.SIDE_BY_SIDE ->
                window.hinges.firstOrNull { it.isVertical }
                    ?.let { it.leftDp.toFloat() to it.rightDp.toFloat() }
            SplitOrientation.STACKED ->
                window.hinges.firstOrNull { !it.isVertical }
                    ?.let { it.topDp.toFloat() to it.bottomDp.toFloat() }
        }

    fun limits(window: SplitWindow): SplitLimits = limits(window, orientationFor(window.hinges))

    fun limits(
        window: SplitWindow,
        orientation: SplitOrientation,
    ): SplitLimits {
        val side = orientation == SplitOrientation.SIDE_BY_SIDE
        val length = if (side) window.widthDp else window.heightDp
        var start = if (side) window.safeLeftDp else window.safeTopDp
        var end = length - if (side) window.safeRightDp else window.safeBottomDp
        when {
            side && window.dockEdge == DockPosition.LEFT -> start = maxOf(start, window.dockExtentDp)
            side && window.dockEdge == DockPosition.RIGHT -> end = minOf(end, length - window.dockExtentDp)
            !side && window.dockEdge == DockPosition.TOP -> start = maxOf(start, window.dockExtentDp)
            !side && window.dockEdge == DockPosition.BOTTOM -> end = minOf(end, length - window.dockExtentDp)
        }
        val region = (end - start).coerceAtLeast(0f)
        val available = region >= 2f * MIN_PANE_DP + DIVIDER_GAP_DP
        val paneShare = if (region > 0f) MIN_PANE_DP / region else 1f
        val min = maxOf(StreamSplit.MIN_FRACTION, paneShare)
        val max = minOf(StreamSplit.MAX_FRACTION, 1f - paneShare)
        return SplitLimits(start, end.coerceAtLeast(start), min, max, available && min <= max)
    }

    /** The divider's centre along the axis for [share], before any hinge snap. */
    private fun positionOf(
        share: Float,
        side: StreamPaneSide,
        limits: SplitLimits,
    ): Float =
        if (side == StreamPaneSide.END) {
            limits.regionEndDp - share * limits.lengthDp
        } else {
            limits.regionStartDp + share * limits.lengthDp
        }

    private fun shareAt(
        position: Float,
        side: StreamPaneSide,
        limits: SplitLimits,
    ): Float {
        val length = limits.lengthDp
        if (length <= 0f) return 0f
        return if (side == StreamPaneSide.END) {
            (limits.regionEndDp - position) / length
        } else {
            (position - limits.regionStartDp) / length
        }
    }

    /**
     * [share] after the hinge's magnet: the hinge's own share when the divider would sit within [HINGE_SNAP_DP] of it
     * and the hinge lies where a split may be; otherwise [share] clamped to the limits.
     */
    fun resolveShare(
        window: SplitWindow,
        share: Float,
    ): Float {
        val orientation = orientationFor(window.hinges)
        val limits = limits(window, orientation)
        val clamped = share.coerceIn(limits.minShare, limits.maxShare.coerceAtLeast(limits.minShare))
        return snapped(window, orientation, limits, clamped)?.first ?: clamped
    }

    /** The snapped share and the hinge it holds, or null when nothing snaps. [share] is already within the limits. */
    private fun snapped(
        window: SplitWindow,
        orientation: SplitOrientation,
        limits: SplitLimits,
        share: Float,
    ): Pair<Float, Pair<Float, Float>>? {
        val hinge = hingeSpan(window, orientation) ?: return null
        val side = streamSideFor(window, orientation)
        val centre = (hinge.first + hinge.second) / 2f
        val position = positionOf(share, side, limits)
        val hingeShare = shareAt(centre, side, limits)
        val inRange = hingeShare in limits.minShare..limits.maxShare
        return if (inRange && abs(position - centre) <= HINGE_SNAP_DP) hingeShare to hinge else null
    }

    /**
     * The panes for [mode] at the Stream's [share] (used only for a split). Home only and Stream only fill the window.
     * With [transient] (a pull or a settle in flight) any share between 0 and 1 draws a split, unclamped and without
     * the hinge's magnet, so a pane can grow from nothing; at rest the share is clamped to the limits and snapped.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun layout(
        window: SplitWindow,
        mode: SplitMode,
        share: Float,
        transient: Boolean = false,
    ): SplitLayout {
        val orientation = orientationFor(window.hinges)
        val side = streamSideFor(window, orientation)
        val whole = PaneRect(0f, 0f, window.widthDp, window.heightDp)
        val limits = limits(window, orientation)
        val splitting = mode == SplitMode.SPLIT && (transient || limits.splitAvailable)
        val streamOnly =
            when {
                transient -> mode == SplitMode.STREAM_ONLY || share >= 1f
                mode == SplitMode.SPLIT && !limits.splitAvailable -> share >= 0.5f
                else -> mode == SplitMode.STREAM_ONLY
            }
        val homeOnly = !splitting && !streamOnly || (transient && share <= 0f && !streamOnly)
        if (streamOnly || homeOnly) {
            return SplitLayout(
                mode = if (streamOnly) SplitMode.STREAM_ONLY else SplitMode.HOME_ONLY,
                orientation = orientation,
                streamSide = side,
                home = if (streamOnly) PaneRect.EMPTY else whole,
                stream = if (streamOnly) clearOfDock(whole, window) else PaneRect.EMPTY,
                divider = null,
                snappedToHinge = false,
                share = if (streamOnly) 1f else 0f,
            )
        }
        val requested = if (transient) share.coerceIn(0f, 1f) else share.coerceIn(limits.minShare, limits.maxShare)
        val snap = if (transient) null else snapped(window, orientation, limits, requested)
        val shown = snap?.first ?: requested
        val position = positionOf(shown, side, limits)
        val gap = snap?.second?.let { (a, b) -> maxOf(b - a, DIVIDER_GAP_DP) } ?: DIVIDER_GAP_DP
        val sideBySide = orientation == SplitOrientation.SIDE_BY_SIDE
        val axisLength = if (sideBySide) window.widthDp else window.heightDp
        val before = position - gap / 2f
        val after = position + gap / 2f
        val hinge = hingeSpan(window, orientation)
        // Along the axis: a Stream at the END side owns [after, length] and Home [0, before]; at START the reverse.
        val streamSpan = if (side == StreamPaneSide.END) after to axisLength else 0f to before
        val homeSpan = if (side == StreamPaneSide.END) 0f to before else after to axisLength
        val streamAxis = avoidHinge(streamSpan, hinge)
        val homeAxis = avoidHinge(homeSpan, hinge)
        val streamRect = clearOfDock(rectOf(streamAxis, sideBySide, window), window)
        var homeRect = rectOf(homeAxis, sideBySide, window)
        val homeTouches = touchesDockEdge(window, homeRect)
        if (!homeTouches) homeRect = reachToDock(homeRect, window)
        return SplitLayout(
            mode = SplitMode.SPLIT,
            orientation = orientation,
            streamSide = side,
            home = homeRect,
            stream = streamRect,
            divider = dividerRect(window, orientation, position),
            snappedToHinge = snap != null,
            share = shown,
        )
    }

    /** [span] cut back to the larger side of [hinge] when it would cover it, so content never crosses a hinge. */
    private fun avoidHinge(
        span: Pair<Float, Float>,
        hinge: Pair<Float, Float>?,
    ): Pair<Float, Float> {
        if (hinge == null || !(span.first < hinge.second && span.second > hinge.first)) return span
        val low = hinge.first - span.first
        val high = span.second - hinge.second
        return if (low >= high) span.first to hinge.first else hinge.second to span.second
    }

    private fun rectOf(
        axis: Pair<Float, Float>,
        sideBySide: Boolean,
        window: SplitWindow,
    ): PaneRect =
        if (sideBySide) {
            PaneRect(axis.first, 0f, axis.second, window.heightDp)
        } else {
            PaneRect(0f, axis.first, window.widthDp, axis.second)
        }

    /**
     * The Stream's surface is cut back off the Dock's room where it reaches the Dock's edge, instead of padding for it,
     * so the same rectangle holds from a split to Stream only. Home pads for the Dock itself.
     */
    private fun clearOfDock(
        rect: PaneRect,
        window: SplitWindow,
    ): PaneRect =
        if (!touchesDockEdge(window, rect)) {
            rect
        } else {
            when (window.dockEdge) {
                DockPosition.LEFT -> rect.copy(left = rect.left + window.dockExtentDp)
                DockPosition.RIGHT -> rect.copy(right = rect.right - window.dockExtentDp)
                DockPosition.TOP -> rect.copy(top = rect.top + window.dockExtentDp)
                DockPosition.BOTTOM -> rect.copy(bottom = rect.bottom - window.dockExtentDp)
            }
        }

    private fun touchesDockEdge(
        window: SplitWindow,
        rect: PaneRect,
    ): Boolean =
        when (window.dockEdge) {
            DockPosition.LEFT -> rect.left <= 0f
            DockPosition.RIGHT -> rect.right >= window.widthDp
            DockPosition.TOP -> rect.top <= 0f
            DockPosition.BOTTOM -> rect.bottom >= window.heightDp
        }

    /** A surface that pads for the Dock on an edge it does not reach reaches over the Dock's room instead. */
    private fun reachToDock(
        rect: PaneRect,
        window: SplitWindow,
    ): PaneRect =
        when (window.dockEdge) {
            DockPosition.LEFT -> rect.copy(left = (rect.left - window.dockExtentDp).coerceAtLeast(0f))
            DockPosition.RIGHT -> rect.copy(right = (rect.right + window.dockExtentDp).coerceAtMost(window.widthDp))
            DockPosition.TOP -> rect.copy(top = (rect.top - window.dockExtentDp).coerceAtLeast(0f))
            DockPosition.BOTTOM -> rect.copy(bottom = (rect.bottom + window.dockExtentDp).coerceAtMost(window.heightDp))
        }

    /** The divider's touch target: [DIVIDER_TARGET_DP] across the axis at [position], along the free cross extent. */
    private fun dividerRect(
        window: SplitWindow,
        orientation: SplitOrientation,
        position: Float,
    ): PaneRect {
        val half = DIVIDER_TARGET_DP / 2f
        return if (orientation == SplitOrientation.SIDE_BY_SIDE) {
            var top = window.safeTopDp
            var bottom = window.heightDp - window.safeBottomDp
            if (window.dockEdge == DockPosition.TOP) top = maxOf(top, window.dockExtentDp)
            if (window.dockEdge == DockPosition.BOTTOM) bottom = minOf(bottom, window.heightDp - window.dockExtentDp)
            PaneRect(position - half, top, position + half, bottom.coerceAtLeast(top))
        } else {
            var left = window.safeLeftDp
            var right = window.widthDp - window.safeRightDp
            if (window.dockEdge == DockPosition.LEFT) left = maxOf(left, window.dockExtentDp)
            if (window.dockEdge == DockPosition.RIGHT) right = minOf(right, window.widthDp - window.dockExtentDp)
            PaneRect(left, position - half, right.coerceAtLeast(left), position + half)
        }
    }

    /** The share the divider would have at [positionDp] along the axis (window dp), for drags and tests. */
    fun shareAtPosition(
        window: SplitWindow,
        positionDp: Float,
    ): Float {
        val orientation = orientationFor(window.hinges)
        return shareAt(positionDp, streamSideFor(window, orientation), limits(window, orientation))
    }

    /** How far, in share, [deltaDp] of movement along the axis moves the divider. Toward the Stream's side grows it. */
    fun shareDeltaFor(
        window: SplitWindow,
        deltaDp: Float,
    ): Float {
        val orientation = orientationFor(window.hinges)
        val limits = limits(window, orientation)
        if (limits.lengthDp <= 0f) return 0f
        val sign = if (streamSideFor(window, orientation) == StreamPaneSide.END) -1f else 1f
        return sign * deltaDp / limits.lengthDp
    }
}

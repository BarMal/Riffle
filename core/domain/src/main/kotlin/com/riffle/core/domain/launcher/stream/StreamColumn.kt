package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.DockPosition

/** An axis-aligned rectangle in window dp. */
data class StreamColumnRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centreX: Float get() = (left + right) / 2f
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * Everything the column's geometry reads, in dp in window coordinates: the window, the system insets, the Dock (its
 * edge, the room it reserves across that edge, and its measured body when the UI knows it).
 */
data class StreamColumnWindow(
    val widthDp: Float,
    val heightDp: Float,
    val dockEdge: DockPosition = DockPosition.RIGHT,
    val dockExtentDp: Float = 0f,
    val safeLeftDp: Float = 0f,
    val safeTopDp: Float = 0f,
    val safeRightDp: Float = 0f,
    val safeBottomDp: Float = 0f,
    /**
     * The Dock's own body as it is now, or null before it is measured. Only the Dock's lower end (beside a side Dock)
     * and its middle (above a bottom Dock) are read from it, so the column follows the Dock's row as it grows.
     */
    val dockBounds: StreamColumnRect? = null,
    /**
     * The Dock's body as it stood at rest (not dipped by a press), or null: the edge the column touches is this
     * body's inner edge, so the column does not move with the dip. Without it the column touches the room the Dock
     * reserves.
     */
    val dockRestBounds: StreamColumnRect? = null,
    /** Vertical hinges that separate the screen into two (a book-like fold), which a card is not drawn across. */
    val verticalHingesDp: List<StreamColumnRect> = emptyList(),
)

/**
 * Where the Stream's card stack stands on an unfolded posture (ADR 0003, Amendment 2): an upright column attached to
 * the Dock's inner side, about a third of the screen wide at rest and up to half while a card is open, so it reads as
 * coming out of the Dock and not as a pane beside it. Home stays one continuous grid under and beside it.
 *
 * Beside a left or right Dock the column stands against the Dock's inner edge (a sliver off it), its bottom at the
 * Dock's bottom end (the live pages are there, so the front card stands beside them and the older cards peek above);
 * above a bottom Dock it is centred on the Dock and stands on it. Opening a card grows the column toward the middle
 * of the screen (wider) and up (taller), the Dock side staying put. Pure geometry; the pieces that read it are the
 * column, the grid's fade ([StreamGridFade]) and the search panel, so all three share one place.
 */
object StreamColumn {
    /** The share of the screen's width the cards take at rest (the grid keeps the other two thirds). */
    const val REST_FRACTION = 1f / 3f

    /** The share an open card may take. */
    const val OPEN_FRACTION = 0.5f

    /** A card is not drawn narrower than this at rest, nor an open card narrower than [MIN_OPEN_WIDTH_DP]. */
    const val MIN_REST_WIDTH_DP = 320f
    const val MIN_OPEN_WIDTH_DP = 440f

    /** The room a resting stack needs: its tallest front card, the peeking cards above it and the gap under it. */
    const val REST_HEIGHT_DP = 264f + StreamCardStackSpec.PITCH_DP * StreamCardStackSpec.VISIBLE_PEEKS + 16f

    /** The room an open card has: its content, the cards behind it as strips and the gap under it, up to the top. */
    const val OPEN_HEIGHT_DP = 480f

    /** The sliver between the Dock's body and the column's cards, so they read as touching without colliding. */
    const val ATTACH_GAP_DP = 4f

    /**
     * The side padding every Stream page keeps around its cards (and the stack's gap under its front card). The
     * column reaches this far past the sliver toward the Dock (under it, where the Dock has the touches), so the
     * cards themselves, not the padding, are what stands against the Dock.
     */
    const val PAGE_INSET_DP = 16f

    private const val DOCK_SIDE_OVERLAP_DP = PAGE_INSET_DP - ATTACH_GAP_DP

    /** The gap kept to the top of the screen's safe area when a card is open. */
    const val TOP_MARGIN_DP = 8f

    /** The column's width at rest on a screen [windowWidthDp] wide. */
    fun restWidthDp(windowWidthDp: Float): Float =
        (windowWidthDp * REST_FRACTION).coerceAtLeast(MIN_REST_WIDTH_DP).coerceAtMost(windowWidthDp)

    /** The column's width with a card fully open. Never narrower than at rest. */
    fun openWidthDp(windowWidthDp: Float): Float =
        (windowWidthDp * OPEN_FRACTION)
            .coerceAtLeast(MIN_OPEN_WIDTH_DP)
            .coerceAtLeast(restWidthDp(windowWidthDp))
            .coerceAtMost(windowWidthDp)

    /**
     * The column for [window] with a card [open] by a fraction from 0 (the stack at rest) to 1 (a card fully open).
     * The Dock side and the bottom stay where they are while it widens and grows taller.
     */
    fun rect(
        window: StreamColumnWindow,
        open: Float = 0f,
    ): StreamColumnRect {
        val p = open.coerceIn(0f, 1f)
        val width = lerp(restWidthDp(window.widthDp), openWidthDp(window.widthDp), p)
        val left: Float
        val bottom: Float
        when (window.dockEdge) {
            DockPosition.RIGHT -> {
                val reserved = window.widthDp - window.safeRightDp - window.dockExtentDp
                left = (window.dockRestBounds?.left ?: reserved) + DOCK_SIDE_OVERLAP_DP - width
                bottom = sideBottom(window)
            }
            DockPosition.LEFT -> {
                val reserved = window.safeLeftDp + window.dockExtentDp
                left = (window.dockRestBounds?.right ?: reserved) - DOCK_SIDE_OVERLAP_DP
                bottom = sideBottom(window)
            }
            else -> {
                val room = (window.safeLeftDp + window.widthDp - window.safeRightDp) / 2f
                val centre = window.dockBounds?.centreX ?: room
                val most = window.widthDp - window.safeRightDp - width
                left = (centre - width / 2f).coerceIn(window.safeLeftDp, most.coerceAtLeast(window.safeLeftDp))
                val reserved = window.heightDp - window.safeBottomDp - window.dockExtentDp
                bottom = (window.dockRestBounds?.top ?: reserved) + DOCK_SIDE_OVERLAP_DP
            }
        }
        val top = topFor(window, bottom, p)
        return StreamColumnRect(left = left, top = top, right = left + width, bottom = bottom).clearOf(window)
    }

    /** The column cut back from a hinge it would cross, on the hinge's far side from the Dock: never across a fold. */
    private fun StreamColumnRect.clearOf(window: StreamColumnWindow): StreamColumnRect =
        window.verticalHingesDp.fold(this) { rect, hinge ->
            val crosses = rect.left < hinge.right && rect.right > hinge.left
            when {
                !crosses -> rect
                window.dockEdge == DockPosition.LEFT || rect.centreX < hinge.centreX -> rect.copy(right = hinge.left)
                else -> rect.copy(left = hinge.right)
            }
        }

    /** Beside a side Dock the stack stands at the Dock's lower end, inside the safe area and tall enough to rest. */
    private fun sideBottom(window: StreamColumnWindow): Float {
        val lowest = window.heightDp - window.safeBottomDp
        val highest = (window.safeTopDp + REST_HEIGHT_DP).coerceAtMost(lowest)
        return (window.dockBounds?.bottom ?: lowest).coerceIn(highest, lowest)
    }

    private fun topFor(
        window: StreamColumnWindow,
        bottom: Float,
        open: Float,
    ): Float {
        val highest = window.safeTopDp + TOP_MARGIN_DP
        val restTop = (bottom - REST_HEIGHT_DP).coerceAtLeast(highest)
        val openTop = (bottom - OPEN_HEIGHT_DP).coerceAtLeast(highest).coerceAtMost(restTop)
        return lerp(restTop, openTop, open)
    }

    private fun lerp(
        from: Float,
        to: Float,
        fraction: Float,
    ): Float = from + (to - from) * fraction
}

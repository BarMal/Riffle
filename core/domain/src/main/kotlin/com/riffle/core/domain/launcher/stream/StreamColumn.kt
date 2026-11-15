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
    /**
     * Whether this is the unfolded column (wider, as tall as the Dock, with bigger cards). False is the geometry a
     * folded phone's "apps behind the cards" column has always had.
     */
    val unfolded: Boolean = false,
    /**
     * How long the Dock's body is in the contextual mode (along its edge), which is the mode whenever the Stream
     * shows; the unfolded column spans it. The Dock says the length it is drawn at: held to the room it has, so never
     * more than the body as drawn. Null before the Dock has said: the column then stands at the Dock's lower end as
     * it did.
     */
    val dockBodyLengthDp: Float? = null,
    /**
     * Whether the Stream shows, so the Dock's body is the one the column stands beside: the unfolded column beside a
     * side Dock spans it exactly (down to [StreamColumn.MIN_SIDE_COLUMN_DP]) and an open card is held to it. False
     * (search, over the Home mode's short Dock): the column is the least height [StreamColumn.restHeightDp] centred on
     * the body, the room search grows in.
     */
    val spansDockBody: Boolean = false,
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

    /** The unfolded column's share and floor: wider, for the larger cards, while the grid stays usable. */
    const val UNFOLDED_REST_FRACTION = 0.38f
    const val UNFOLDED_MIN_REST_WIDTH_DP = 360f

    /** The tallest front card a column rests with, at the folded sizes (the player's card). */
    const val TALLEST_FRONT_CARD_DP = 264f

    /** The most height the unfolded column takes above a bottom Dock. */
    const val BOTTOM_DOCK_MAX_HEIGHT_DP = 560f

    /** The share an open card may take. */
    const val OPEN_FRACTION = 0.5f

    /** A card is not drawn narrower than this at rest, nor an open card narrower than [MIN_OPEN_WIDTH_DP]. */
    const val MIN_REST_WIDTH_DP = 320f
    const val MIN_OPEN_WIDTH_DP = 440f

    /** The room a resting stack needs: its tallest front card, the peeking cards above it and the gap under it. */
    const val REST_HEIGHT_DP = 264f + StreamCardStackSpec.PITCH_DP * StreamCardStackSpec.VISIBLE_PEEKS + 16f

    /**
     * The least the unfolded column beside a side Dock is tall: a Dock body shorter than this is centred on and the
     * column takes this much (a card of the least height, its strip and a tab or two). Taller bodies are spanned.
     */
    const val MIN_SIDE_COLUMN_DP = 240f

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
    fun restWidthDp(
        windowWidthDp: Float,
        unfolded: Boolean = false,
    ): Float =
        if (unfolded) {
            (windowWidthDp * UNFOLDED_REST_FRACTION).coerceAtLeast(UNFOLDED_MIN_REST_WIDTH_DP)
        } else {
            (windowWidthDp * REST_FRACTION).coerceAtLeast(MIN_REST_WIDTH_DP)
        }.coerceAtMost(windowWidthDp)

    /** The column's width with a card fully open. Never narrower than at rest. */
    fun openWidthDp(
        windowWidthDp: Float,
        unfolded: Boolean = false,
    ): Float =
        (windowWidthDp * OPEN_FRACTION)
            .coerceAtLeast(MIN_OPEN_WIDTH_DP)
            .coerceAtLeast(restWidthDp(windowWidthDp, unfolded))
            .coerceAtMost(windowWidthDp)

    /**
     * The least the column is tall at rest: the tallest front card, [StreamCardStackSpec.VISIBLE_PEEKS] strips above it
     * and the gap under it, at the sizes of the posture. Never less than the folded [REST_HEIGHT_DP].
     */
    fun restHeightDp(unfolded: Boolean = false): Float {
        val sizes = StreamCardSizes.forPosture(unfolded)
        return maxOf(
            REST_HEIGHT_DP,
            TALLEST_FRONT_CARD_DP * sizes.fixedHeightFactor +
                sizes.pitchDp * StreamCardStackSpec.VISIBLE_PEEKS + PAGE_INSET_DP,
        )
    }

    /**
     * The column for [window] with a card [open] by a fraction from 0 (the stack at rest) to 1 (a card fully open).
     * The Dock side and the bottom stay where they are while it widens and grows taller.
     */
    fun rect(
        window: StreamColumnWindow,
        open: Float = 0f,
    ): StreamColumnRect {
        val p = open.coerceIn(0f, 1f)
        val wanted =
            lerp(restWidthDp(window.widthDp, window.unfolded), openWidthDp(window.widthDp, window.unfolded), p)
        val width: Float
        val left: Float
        var bottom: Float
        var restTop: Float? = null
        when (window.dockEdge) {
            DockPosition.RIGHT -> {
                val reserved = window.widthDp - window.safeRightDp - window.dockExtentDp
                val right = (window.dockRestBounds?.left ?: reserved) + DOCK_SIDE_OVERLAP_DP
                // On a narrow window the width's floor is more than the room beside the Dock: stay on screen.
                width = wanted.coerceAtMost((right - window.safeLeftDp).coerceAtLeast(0f))
                left = right - width
                bottom = sideBottom(window)
                if (window.unfolded) {
                    sideSpan(window)?.let { (top, low) ->
                        restTop = top
                        bottom = low
                    }
                }
            }
            DockPosition.LEFT -> {
                val reserved = window.safeLeftDp + window.dockExtentDp
                left = (window.dockRestBounds?.right ?: reserved) - DOCK_SIDE_OVERLAP_DP
                width = wanted.coerceAtMost((window.widthDp - window.safeRightDp - left).coerceAtLeast(0f))
                bottom = sideBottom(window)
                if (window.unfolded) {
                    sideSpan(window)?.let { (top, low) ->
                        restTop = top
                        bottom = low
                    }
                }
            }
            else -> {
                width = wanted
                val room = (window.safeLeftDp + window.widthDp - window.safeRightDp) / 2f
                val centre = window.dockBounds?.centreX ?: room
                val most = window.widthDp - window.safeRightDp - width
                left = (centre - width / 2f).coerceIn(window.safeLeftDp, most.coerceAtLeast(window.safeLeftDp))
                val reserved = window.heightDp - window.safeBottomDp - window.dockExtentDp
                bottom = (window.dockRestBounds?.top ?: reserved) + DOCK_SIDE_OVERLAP_DP
            }
        }
        val top = topFor(window, bottom, p, restTop)
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

    /**
     * The unfolded column beside a side Dock. While the Stream shows ([StreamColumnWindow.spansDockBody]) its top and
     * bottom are the Dock body's as it is drawn (the Dock in the contextual mode: [StreamColumnWindow.dockBodyLengthDp]
     * long about the middle of its measured body, a length the Dock holds to its room), inside the safe area; a body
     * shorter than [MIN_SIDE_COLUMN_DP] is centred on instead, for the least a card and its strip can stand in. (It used
     * to be at least [restHeightDp] tall, 512 dp: against a body of 489 dp the stack stood 11 dp over it at each end.)
     * Without the Stream (search over the Home mode's short Dock) it is [restHeightDp] tall at least, centred on the
     * body, as search always stood. Null until the Dock is measured.
     */
    private fun sideSpan(window: StreamColumnWindow): Pair<Float, Float>? {
        val body = window.dockBounds ?: return null
        val highest = window.safeTopDp + if (window.spansDockBody) 0f else TOP_MARGIN_DP
        val lowest = window.heightDp - window.safeBottomDp
        val centre = (body.top + body.bottom) / 2f
        val half = (window.dockBodyLengthDp ?: body.height) / 2f
        val wanted = if (window.spansDockBody) MIN_SIDE_COLUMN_DP else restHeightDp(unfolded = true)
        val least = wanted.coerceAtMost((lowest - highest).coerceAtLeast(0f))
        var top = (centre - half).coerceAtLeast(highest)
        var bottom = (centre + half).coerceAtMost(lowest)
        if (bottom - top < least) {
            top = centre - least / 2f
            bottom = top + least
            if (top < highest) {
                bottom += highest - top
                top = highest
            }
            if (bottom > lowest) {
                top -= bottom - lowest
                bottom = lowest
            }
            top = top.coerceAtLeast(highest)
        }
        return top to bottom
    }

    private fun topFor(
        window: StreamColumnWindow,
        bottom: Float,
        open: Float,
        restTopOverride: Float?,
    ): Float {
        val highest = window.safeTopDp + TOP_MARGIN_DP
        val restTop =
            restTopOverride
                ?: if (window.unfolded && window.dockEdge == DockPosition.BOTTOM) {
                    // Above a bottom Dock: the room between the safe top and the Dock, at most 560 dp.
                    val height = (bottom - highest).coerceIn(restHeightDp(true), BOTTOM_DOCK_MAX_HEIGHT_DP)
                    (bottom - height).coerceAtLeast(highest)
                } else {
                    (bottom - restHeightDp(window.unfolded)).coerceAtLeast(highest)
                }
        // Beside a side Dock the unfolded column is the Dock body's span and stays it: an open card is held to it too.
        if (restTopOverride != null && window.spansDockBody) return restTop
        val openTop = (bottom - maxOf(OPEN_HEIGHT_DP, bottom - restTop)).coerceAtLeast(highest).coerceAtMost(restTop)
        return lerp(restTop, openTop, open)
    }

    private fun lerp(
        from: Float,
        to: Float,
        fraction: Float,
    ): Float = from + (to - from) * fraction
}

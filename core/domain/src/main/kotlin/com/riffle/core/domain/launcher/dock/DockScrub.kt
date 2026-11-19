package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.gestures.HoldMenuRule
import kotlin.math.abs

/**
 * Hold, then slide along the Dock to change stack (ADR 0003, The Dock is the handle): while the Stream shows, a press
 * on the Dock's contextual row that is held for the long-press timeout arms the scrub, and sliding along the run from
 * there scrubs. A plain drag along the row is not a scrub: it scrolls the row, as the Dock always did. The tile under
 * the finger becomes the current one as the finger enters it, and the Stream shows that page at once.
 *
 * Holding without moving is the tile's own long-press menu; moving past touch slop after the hold closes that menu and
 * starts the scrub ([HoldMenuRule], the rule the Home drag shares).
 *
 * Everything that decides anything is here and pure: whether a press has armed the scrub ([armed]), whether the slide
 * from it is a scrub ([claims]), which tile the finger is on
 * with a little hysteresis at the tile boundaries so it does not flicker ([tileAt]), and which way holding near an end
 * of the row scrolls it ([edgeDirection]). Ticks are [DockChoice.shouldTick]'s, and the pace of the edge scroll is
 * [DockChoice.edgeScrollDp]'s: the scrub reuses hold to choose's selection.
 */
object DockScrub {
    /** How far past a tile's own extent the finger must go before the next tile takes over, in dp. */
    const val HYSTERESIS_DP: Float = 6f

    /** How close to an end of the row the finger must be for the row to scroll toward it, in dp. */
    const val EDGE_ZONE_DP: Float = 24f

    /**
     * Whether a press that has been down [heldMillis] has armed the scrub: it was held for the platform's long-press
     * timeout ([longPressMillis]) and has stayed within touch slop ([touchSlopPx]) of where it landed ([travelPx]).
     * A finger that moved before the hold is a scroll, a pull or a press, never a scrub.
     */
    fun armed(
        heldMillis: Long,
        longPressMillis: Long,
        travelPx: Float,
        touchSlopPx: Float,
    ): Boolean = heldMillis >= longPressMillis && !HoldMenuRule.leavesMenu(travelPx, 0f, touchSlopPx)

    /**
     * Whether a slide of ([dx], [dy]) px from where the armed finger rests is a scrub: it left touch slop
     * ([touchSlopPx], which also closes the tile's menu) and leads along the run of a Dock pulled in [pull], more than
     * it leads across it.
     */
    fun claims(
        pull: DockPullDirection,
        dx: Float,
        dy: Float,
        touchSlopPx: Float,
    ): Boolean {
        if (!HoldMenuRule.leavesMenu(dx, dy, touchSlopPx)) return false
        val along = abs(dx * pull.unitY - dy * pull.unitX)
        val across = abs(pull.along(dx, dy))
        return along > across
    }

    /**
     * The key of the tile current with the finger at [positionPx] along the run, [currentKey] being the one it was
     * until now (null before the first). Only [selectable] items count: the others (a pair, a pinned app with no
     * page) are passed over, and so is a gap between tiles, which keeps [currentKey]. The current tile keeps the
     * finger while it is within [hysteresisPx] of its extent, so a finger resting on a boundary does not flicker.
     */
    fun tileAt(
        positionPx: Float,
        items: List<DockChoiceItem>,
        selectable: Set<String>,
        currentKey: String?,
        hysteresisPx: Float,
    ): String? {
        val candidates = items.filter { item -> item.key in selectable }
        val current = candidates.firstOrNull { item -> item.key == currentKey }
        val staysOnCurrent =
            current != null &&
                positionPx >= current.startPx - hysteresisPx &&
                positionPx <= current.endPx + hysteresisPx
        val under = candidates.firstOrNull { item -> positionPx >= item.startPx && positionPx <= item.endPx }
        return when {
            staysOnCurrent -> currentKey
            under != null -> under.key
            else -> currentKey
        }
    }

    /**
     * Which way the finger at [fingerPx] scrolls the row whose viewport spans [viewportStartPx] to [viewportEndPx]: -1
     * toward its start, +1 toward its end, 0 not at all. Only within [zonePx] of an end (a finger beyond it counts)
     * and only while tiles are hidden that way ([canScrollToStart], [canScrollToEnd]).
     */
    fun edgeDirection(
        fingerPx: Float,
        viewportStartPx: Float,
        viewportEndPx: Float,
        zonePx: Float,
        canScrollToStart: Boolean,
        canScrollToEnd: Boolean,
    ): Int =
        when {
            viewportEndPx <= viewportStartPx -> 0
            canScrollToStart && fingerPx < viewportStartPx + zonePx -> -1
            canScrollToEnd && fingerPx > viewportEndPx - zonePx -> 1
            else -> 0
        }
}

package com.riffle.core.domain.launcher.gestures

import kotlin.math.hypot

/**
 * "The menu unless it moves": a long-press that holds still opens the item's menu; the same press, moved past touch
 * slop, closes the menu and becomes a drag. One rule for every item that is both a menu and a drag handle: a Home item
 * (the Home drag), a pinned Dock item (reorder in Home mode) and a Dock tile (the scrub in the contextual mode).
 */
object HoldMenuRule {
    /** Whether a finger now ([dxPx], [dyPx]) from where the hold landed has moved past [touchSlopPx]: menu closes. */
    fun leavesMenu(
        dxPx: Float,
        dyPx: Float,
        touchSlopPx: Float,
    ): Boolean = hypot(dxPx, dyPx) > touchSlopPx
}

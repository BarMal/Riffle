package com.riffle.core.domain.launcher.home

/**
 * Which edge of a home layout the dock occupies.
 *
 * This started life as the Cards surface's rail side, because the rail was the only edge-anchored
 * strip in the launcher. It is the same question either way -- which edge does the persistent strip
 * of apps and live content run along -- so it is named for the strip rather than for the one
 * surface that happened to have it first, and is configured per [HomeLayoutKey]: a phone in
 * portrait and a tablet want different answers, and the layout key is already how this codebase
 * says "per device class and view mode".
 */
enum class DockPosition {
    LEADING,
    TRAILING,
    TOP,
    BOTTOM,
}

/** True for the two edges where the dock runs as a horizontal strip instead of a side column. */
val DockPosition.isHorizontalEdge: Boolean
    get() = this == DockPosition.TOP || this == DockPosition.BOTTOM

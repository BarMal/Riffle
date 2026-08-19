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

/**
 * The grid the workspace actually gets, once the dock has taken what it needs.
 *
 * A dock on a side edge reserves width from the pages beside it, and takes that width out of the
 * grid as a whole column rather than out of every cell -- so the icons stay the size the user chose
 * and there is simply one fewer of them across. A dock on a horizontal edge reserves height, which
 * the pages already give it, so the grid is untouched.
 *
 * [GridSettings.dimensions] keeps the column the dock borrowed, so moving the dock back to the
 * bottom gives it back rather than leaving the workspace permanently narrower.
 */
val HomeLayout.workspaceGrid: GridDimensions
    get() = settings.grid.dimensions.workspaceGridFor(dock)

/** [workspaceGrid] for callers holding the two pieces rather than an assembled layout. */
fun GridDimensions.workspaceGridFor(dock: DockModel): GridDimensions =
    when {
        !dock.isEnabled -> this
        dock.position?.isHorizontalEdge != false -> this
        else -> copy(columns = (columns - 1).coerceAtLeast(MIN_GRID_DIMENSION))
    }

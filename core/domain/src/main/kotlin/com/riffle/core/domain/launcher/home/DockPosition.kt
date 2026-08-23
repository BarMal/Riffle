package com.riffle.core.domain.launcher.home

/**
 * Which physical edge of a home layout the dock occupies.
 *
 * These are absolute edges, not layout-direction-relative ones: [LEFT] is the physical left edge in
 * every locale and [RIGHT] the physical right. A dock is a place the user explicitly points at in
 * settings, so it stays where they put it rather than mirroring with text direction the way
 * start/end content does. (Content *inside* the dock -- icon order, labels -- still mirrors on its
 * own; that is independent of which edge the strip sits on.)
 *
 * Configured per [HomeLayoutKey]: a phone in portrait and a tablet want different answers, and the
 * layout key is already how this codebase says "per device class and view mode".
 */
enum class DockPosition {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

/** True for the two edges where the dock runs as a horizontal strip instead of a side column. */
val DockPosition.isHorizontalEdge: Boolean
    get() = this == DockPosition.TOP || this == DockPosition.BOTTOM

/**
 * Parses a stored [DockPosition] name, mapping the legacy direction-relative names to the absolute
 * edges they became. Layouts written before the rename hold "LEADING"/"TRAILING"; in the
 * left-to-right layouts they were all authored in, those were the left and right edges. Returns null
 * for anything unrecognised, so a caller can fall back to its default.
 */
fun dockPositionFromStoredName(name: String): DockPosition? =
    when (name) {
        "LEADING" -> DockPosition.LEFT
        "TRAILING" -> DockPosition.RIGHT
        else -> DockPosition.entries.firstOrNull { position -> position.name == name }
    }

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

/**
 * The edges a layout can actually put its persistent strip on.
 *
 * A Cards layout positions its stage rail, which is drawn on all four. Every other view mode
 * positions the home dock, which is placed on three -- nothing puts the home dock on the top edge,
 * and a setting that silently does nothing is worse than one that is simply not offered.
 *
 * The distinction lives here rather than in the settings screen because it is a fact about which
 * surface a view mode draws, not about how the control looks.
 */
val LauncherViewMode.placeableDockPositions: List<DockPosition>
    get() =
        when (this) {
            LauncherViewMode.CARD_INTERFACE -> DockPosition.entries.toList()
            LauncherViewMode.STANDARD_APP_DRAWER,
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            -> DockPosition.entries.filterNot { position -> position == DockPosition.TOP }
        }

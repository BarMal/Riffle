package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.DockPosition

/**
 * Which way the dock is pulled to switch surface: away from its edge, toward the screen interior.
 *
 * Directions are physical screen directions (like [DockPosition], they do not mirror with layout
 * direction), in screen coordinates where x grows rightwards and y grows downwards. [unitX] and
 * [unitY] are the unit vector of the direction in those coordinates.
 */
enum class DockPullDirection(
    val unitX: Float,
    val unitY: Float,
) {
    UP(unitX = 0f, unitY = -1f),
    DOWN(unitX = 0f, unitY = 1f),
    LEFT(unitX = -1f, unitY = 0f),
    RIGHT(unitX = 1f, unitY = 0f),
    ;

    /**
     * The signed length of the drag ([dx], [dy]) along this direction: positive toward the interior,
     * negative back toward the edge. Only the component along the direction counts; movement across
     * it is ignored. Units are whatever the delta is in (dp for the transition controller).
     */
    fun along(
        dx: Float,
        dy: Float,
    ): Float = dx * unitX + dy * unitY

    /**
     * How far the drag ([dx], [dy]) pulls the dock: [along], but never below zero, so a drag away
     * from the interior (into the edge) pulls nothing.
     */
    fun pullDistance(
        dx: Float,
        dy: Float,
    ): Float = maxOf(0f, along(dx, dy))
}

/** The natural pull for a dock on this edge: bottom pulls up, top down, left right, right left. */
val DockPosition.pullDirection: DockPullDirection
    get() =
        when (this) {
            DockPosition.BOTTOM -> DockPullDirection.UP
            DockPosition.TOP -> DockPullDirection.DOWN
            DockPosition.LEFT -> DockPullDirection.RIGHT
            DockPosition.RIGHT -> DockPullDirection.LEFT
        }

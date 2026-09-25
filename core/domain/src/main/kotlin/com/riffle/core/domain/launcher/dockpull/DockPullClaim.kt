package com.riffle.core.domain.launcher.dockpull

import kotlin.math.abs
import kotlin.math.hypot

/** What a drag that started on the dock body means for the dock pull, so far. */
enum class DockPullClaim {
    /** Still inside touch slop: nothing is decided and the dock's own children keep the touch. */
    UNDECIDED,

    /** A pull: the drag left slop mostly along the natural pull direction, toward the interior. */
    CLAIM,

    /**
     * Not a pull: the drag left slop along the dock's run (a section scroll), across the pull, or
     * back into the edge. The pull leaves the rest of this touch alone.
     */
    REJECT,
}

/**
 * Whether a drag of ([dx], [dy]) px from where the finger landed on the dock claims the pull in
 * [direction]. Undecided until the drag is [touchSlopPx] long; then a pull only when its component
 * along [direction] is positive and larger than the component across it, so drags along a dock's
 * run keep scrolling its sections.
 */
fun dockPullClaimFor(
    direction: DockPullDirection,
    dx: Float,
    dy: Float,
    touchSlopPx: Float,
): DockPullClaim {
    if (hypot(dx, dy) < touchSlopPx) return DockPullClaim.UNDECIDED
    val along = direction.along(dx, dy)
    val across = abs(dx * direction.unitY - dy * direction.unitX)
    return if (along > 0f && along > across) DockPullClaim.CLAIM else DockPullClaim.REJECT
}

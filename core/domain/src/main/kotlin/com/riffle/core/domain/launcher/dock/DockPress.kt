package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.gestures.GestureThresholds
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pressing the Dock down and releasing (ADR 0003, Amendment 3, One gesture): a short drag toward the Dock's own edge
 * that passes [COMMIT_DP], then release, toggles between the Home and the contextual mode. The way it moves is the
 * opposite of the pull ([DockPullDirection]), which still opens the Library.
 *
 * Pure, so the claim and the threshold are tested without a screen. The UI side exposes [progress] and the release so
 * a later slice can draw the dip and the bounce from them.
 */
object DockPress {
    /** How far the Dock has to be pressed down for a release to count, in dp. */
    const val COMMIT_DP: Float = GestureThresholds.DOCK_PRESS_COMMIT_DP

    /**
     * How far the drag ([dxDp], [dyDp]) has pressed the Dock toward its edge, in dp, given the [pull] direction the
     * Dock
     * is pulled in: zero when the finger is at or beyond where it landed the other way.
     */
    fun travelDp(
        pull: DockPullDirection,
        dxDp: Float,
        dyDp: Float,
    ): Float = maxOf(0f, -pull.along(dxDp, dyDp))

    /** How far through the press the Dock is, 0 (not pressed) to 1 (a release now commits). */
    fun progress(travelDp: Float): Float = (travelDp / COMMIT_DP).coerceIn(0f, 1f)

    /** Whether releasing with the Dock pressed [travelDp] down toggles the mode. */
    fun commits(travelDp: Float): Boolean = travelDp >= COMMIT_DP

    /**
     * Whether a drag of ([dx], [dy]) px from where the finger landed claims the press, given the [pull] direction:
     * undecided inside [touchSlopPx], then a press only when it runs toward the Dock's edge and more along that than
     * across it, so drags along the run keep scrolling the Dock. [DockPullClaim.CLAIM] means a press.
     */
    fun claimFor(
        pull: DockPullDirection,
        dx: Float,
        dy: Float,
        touchSlopPx: Float,
    ): DockPullClaim {
        if (hypot(dx, dy) < touchSlopPx) return DockPullClaim.UNDECIDED
        val toward = -pull.along(dx, dy)
        val across = abs(dx * pull.unitY - dy * pull.unitX)
        return if (toward > 0f && toward > across) DockPullClaim.CLAIM else DockPullClaim.REJECT
    }
}

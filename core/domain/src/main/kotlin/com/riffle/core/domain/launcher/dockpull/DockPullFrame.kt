package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.ModeDockEdges

/**
 * How one of the two mode surfaces is drawn during a dock pull.
 *
 * [offsetFraction] is its translation along the pull's direction, as a fraction of the window's
 * extent on that axis (positive toward the interior the dock is pulled into); [alpha] its opacity.
 */
data class DockPullSurfaceFrame(
    val offsetFraction: Float,
    val alpha: Float,
)

/**
 * Everything the UI needs to draw one frame of a dock pull (Decision 4), from the transition state
 * and each surface's dock edge. Pure: the UI multiplies the fractions by the window size.
 *
 * - [dockEdge]: the edge the dock is drawn on -- the origin surface's while the pull tracks or
 *   springs back, the destination's once a release commits (its icons re-orient there as it snaps
 *   in).
 * - [dockOffsetFraction]: how far the dock is displaced in from [dockEdge], along that edge's pull
 *   direction, as a fraction of the window's extent on that axis. While tracking it is the progress,
 *   so the dock follows the finger 1:1 (the pull's travel is that same extent).
 * - [dockBackgroundAlpha]: the dock background's alpha multiplier.
 * - [outgoing] / [incoming]: the origin and destination surfaces. The outgoing surface moves with
 *   the dock; the incoming one follows in from the side the dock came from. Under reduced motion
 *   nothing moves and the two surfaces crossfade instead.
 */
data class DockPullFrame(
    val dockEdge: DockPosition,
    val dockOffsetFraction: Float,
    val dockBackgroundAlpha: Float,
    val outgoing: DockPullSurfaceFrame,
    val incoming: DockPullSurfaceFrame,
)

/** The frame for this state, given each surface's resolved dock [edges]. */
fun DockPullTransitionState.frame(edges: ModeDockEdges): DockPullFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val outgoing: DockPullSurfaceFrame
    val incoming: DockPullSurfaceFrame
    if (reducedMotion) {
        outgoing = DockPullSurfaceFrame(offsetFraction = 0f, alpha = 1f - clamped)
        incoming = DockPullSurfaceFrame(offsetFraction = 0f, alpha = clamped)
    } else {
        outgoing = DockPullSurfaceFrame(offsetFraction = clamped, alpha = 1f)
        incoming = DockPullSurfaceFrame(offsetFraction = clamped - 1f, alpha = 1f)
    }
    val dockEdge: DockPosition
    val dockOffset: Float
    when (this) {
        is DockPullTransitionState.Idle -> {
            dockEdge = edges.edgeFor(surface)
            dockOffset = 0f
        }

        is DockPullTransitionState.Tracking -> {
            dockEdge = edges.edgeFor(from)
            dockOffset = clamped
        }

        is DockPullTransitionState.Settling ->
            if (outcome == SettleOutcome.COMMIT) {
                // The dock snaps to the destination's edge on release, displaced in from it by as
                // far as it had been pulled, and settles onto it as the settle completes.
                dockEdge = edges.edgeFor(to)
                dockOffset = releaseProgress * (1f - settleFraction)
            } else {
                dockEdge = edges.edgeFor(from)
                dockOffset = clamped
            }
    }
    return DockPullFrame(
        dockEdge = dockEdge,
        dockOffsetFraction = if (reducedMotion) 0f else dockOffset,
        dockBackgroundAlpha = dockBackgroundAlpha,
        outgoing = outgoing,
        incoming = incoming,
    )
}

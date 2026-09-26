package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.DockOrientation
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface

/** How a released (or interrupted-then-released) pull ends. */
enum class SettleOutcome {
    /** The switch goes through: progress runs on to 1 and the destination surface becomes current. */
    COMMIT,

    /** The switch is abandoned: progress springs back to 0 and the origin surface stays current. */
    CANCEL,
}

/**
 * Where a dock-pull mode transition is (#1206): Idle -> Tracking -> Settling -> Idle.
 *
 * Progress runs 0..1 from [Tracking.from] (0) to [Tracking.to] (1). Nothing here animates: the UI
 * tracks the finger into [Tracking], animates a [Settling] state's progress toward its
 * [Settling.targetProgress] and reports it back, and finishes into the new [Idle].
 *
 * [reducedMotion] is carried through every state untouched, so whoever draws the transition can
 * swap the slide for a short crossfade to the same end state.
 */
sealed interface DockPullTransitionState {
    val reducedMotion: Boolean

    /** How far the transition is from its origin (0) to its destination (1). */
    val progress: Float

    /**
     * The surface this state is heading for: the current one when idle, the pull's destination while
     * tracking, and whichever surface the settle ends on while settling.
     */
    val targetSurface: ModeSurface

    /** No transition: [surface] is on screen. */
    data class Idle(
        val surface: ModeSurface,
        override val reducedMotion: Boolean = false,
    ) : DockPullTransitionState {
        override val progress: Float get() = 0f
        override val targetSurface: ModeSurface get() = surface
    }

    /**
     * The finger holds the dock. [dragXDp], [dragYDp] are the total drag since this gesture began;
     * only its component along [direction] moves [progress], from [startProgress] (0 for a fresh
     * pull, the settle's progress when a pull caught a settle mid-flight). [travelDp] is the pull
     * length that takes progress from 0 to 1.
     */
    data class Tracking(
        val from: ModeSurface,
        val to: ModeSurface,
        val direction: DockPullDirection,
        val travelDp: Float,
        val startProgress: Float = 0f,
        val dragXDp: Float = 0f,
        val dragYDp: Float = 0f,
        override val reducedMotion: Boolean = false,
    ) : DockPullTransitionState {
        init {
            require(travelDp > 0f) { "A pull needs a positive travel distance: $travelDp" }
            require(startProgress in 0f..1f) { "Progress runs 0..1: $startProgress" }
        }

        /** Signed distance this gesture has moved along [direction]. */
        val gestureDistanceDp: Float get() = direction.along(dragXDp, dragYDp)

        override val progress: Float
            get() = (startProgress + gestureDistanceDp / travelDp).coerceIn(0f, 1f)

        override val targetSurface: ModeSurface get() = to
    }

    /**
     * The finger has let go and the transition runs to [outcome]'s end. [releaseProgress] is where
     * it was let go; [progress] is where the settle animation has got to since.
     */
    data class Settling(
        val from: ModeSurface,
        val to: ModeSurface,
        val direction: DockPullDirection,
        val travelDp: Float,
        val outcome: SettleOutcome,
        val releaseProgress: Float,
        override val progress: Float = releaseProgress,
        override val reducedMotion: Boolean = false,
    ) : DockPullTransitionState {
        init {
            require(travelDp > 0f) { "A pull needs a positive travel distance: $travelDp" }
            require(releaseProgress in 0f..1f && progress in 0f..1f) {
                "Progress runs 0..1: $releaseProgress, $progress"
            }
        }

        /** Where [outcome] leaves progress: 1 for a commit, 0 for a cancel. */
        val targetProgress: Float
            get() =
                when (outcome) {
                    SettleOutcome.COMMIT -> 1f
                    SettleOutcome.CANCEL -> 0f
                }

        /** How much of the way from [releaseProgress] to [targetProgress] the settle has come, 0..1. */
        val settleFraction: Float
            get() {
                val span = targetProgress - releaseProgress
                return if (span == 0f) 1f else ((progress - releaseProgress) / span).coerceIn(0f, 1f)
            }

        /** The surface on screen once the settle finishes. */
        val endSurface: ModeSurface
            get() =
                when (outcome) {
                    SettleOutcome.COMMIT -> to
                    SettleOutcome.CANCEL -> from
                }

        override val targetSurface: ModeSurface get() = endSurface
    }
}

/**
 * The dock background's alpha multiplier while the dock is pulled [progress] of the way: fully
 * opaque at rest, fading out in proportion to the pull.
 */
fun dockBackgroundAlphaForPull(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

/**
 * The dock background's alpha multiplier during a settle: from the alpha it was released at
 * ([dockBackgroundAlphaForPull] of the release progress) back up to fully opaque as the settle
 * completes, whichever way it goes.
 */
fun dockBackgroundAlphaForSettle(
    releaseProgress: Float,
    settleFraction: Float,
): Float {
    val released = dockBackgroundAlphaForPull(releaseProgress)
    return released + (1f - released) * settleFraction.coerceIn(0f, 1f)
}

/** The dock background's alpha multiplier (0..1) for this state; 1 when idle. */
val DockPullTransitionState.dockBackgroundAlpha: Float
    get() =
        when (this) {
            is DockPullTransitionState.Idle -> 1f
            is DockPullTransitionState.Tracking -> dockBackgroundAlphaForPull(progress)
            is DockPullTransitionState.Settling -> dockBackgroundAlphaForSettle(releaseProgress, settleFraction)
        }

/** The edge the dock is heading for in this state, given each surface's resolved [edges]. */
fun DockPullTransitionState.targetDockEdge(edges: ModeDockEdges): DockPosition = edges.edgeFor(targetSurface)

/** The orientation the dock's icons take for this state's [targetDockEdge]. */
fun DockPullTransitionState.targetDockOrientation(edges: ModeDockEdges): DockOrientation =
    edges.orientationFor(targetSurface)

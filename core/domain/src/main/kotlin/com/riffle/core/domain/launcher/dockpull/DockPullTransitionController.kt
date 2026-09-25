package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Idle
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Settling
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Tracking
import kotlin.math.abs

/**
 * When a released pull commits, in density-independent units.
 *
 * A release commits when the pull has come [commitFraction] of its travel, or when it is flung
 * toward the interior at [commitVelocityDpPerSecond] or faster. A fling back toward the edge at that
 * speed cancels, however far the pull had come. Velocity only counts once the gesture has moved
 * [minimumFlingDistanceDp] along the pull, so a tap or a twitch on the dock never switches surface.
 */
data class DockPullThresholds(
    val commitFraction: Float = DEFAULT_COMMIT_FRACTION,
    val commitVelocityDpPerSecond: Float = DEFAULT_COMMIT_VELOCITY_DP_PER_SECOND,
    val minimumFlingDistanceDp: Float = DEFAULT_MINIMUM_FLING_DISTANCE_DP,
) {
    init {
        require(commitFraction > 0f && commitFraction < 1f) { "Commit fraction must be in (0, 1): $commitFraction" }
        require(commitVelocityDpPerSecond > 0f) { "Commit velocity must be positive: $commitVelocityDpPerSecond" }
        require(minimumFlingDistanceDp >= 0f) { "Minimum fling distance must be >= 0: $minimumFlingDistanceDp" }
    }

    /**
     * How a pull released at [progress] ends, having moved [gestureDistanceDp] along the pull in this
     * gesture and moving at [velocityDpPerSecond] along it (positive toward the interior).
     */
    fun outcomeFor(
        progress: Float,
        gestureDistanceDp: Float,
        velocityDpPerSecond: Float,
    ): SettleOutcome {
        val isFling =
            abs(gestureDistanceDp) >= minimumFlingDistanceDp &&
                abs(velocityDpPerSecond) >= commitVelocityDpPerSecond
        return when {
            isFling && velocityDpPerSecond > 0f -> SettleOutcome.COMMIT
            isFling -> SettleOutcome.CANCEL
            progress >= commitFraction -> SettleOutcome.COMMIT
            else -> SettleOutcome.CANCEL
        }
    }

    companion object {
        const val DEFAULT_COMMIT_FRACTION: Float = 0.4f
        const val DEFAULT_COMMIT_VELOCITY_DP_PER_SECOND: Float = 600f
        const val DEFAULT_MINIMUM_FLING_DISTANCE_DP: Float = 8f
    }
}

/**
 * The pure dock-pull transition state machine (#1206). Each event takes the current state and returns
 * the next; an event that means nothing in the current state returns it unchanged.
 *
 * - Idle --[start]--> Tracking (fresh pull toward the other surface)
 * - Tracking --[drag]--> Tracking; --[release]--> Settling(commit or cancel); --[cancel]--> Settling(cancel)
 * - Settling --[settleProgress]--> Settling; --[finish]--> Idle(end surface)
 * - Settling --[start]--> Tracking, resuming from the settle's current progress (interruption)
 */
class DockPullTransitionController(
    val thresholds: DockPullThresholds = DockPullThresholds(),
) {
    /**
     * A pull begins on the dock body. From [Idle], [direction] (the dock edge's
     * [com.riffle.core.domain.launcher.home.pullDirection]) and [travelDp] start a fresh pull toward
     * the other surface. From [Settling], the finger has caught the dock mid-settle: tracking resumes
     * from the current progress, keeping the original pull's direction and travel. [reducedMotion] is
     * carried into the new state.
     */
    fun start(
        state: DockPullTransitionState,
        direction: DockPullDirection,
        travelDp: Float,
        reducedMotion: Boolean = state.reducedMotion,
    ): DockPullTransitionState =
        when (state) {
            is Idle ->
                Tracking(
                    from = state.surface,
                    to = state.surface.other,
                    direction = direction,
                    travelDp = travelDp,
                    reducedMotion = reducedMotion,
                )

            is Settling ->
                Tracking(
                    from = state.from,
                    to = state.to,
                    direction = state.direction,
                    travelDp = state.travelDp,
                    startProgress = state.progress,
                    reducedMotion = reducedMotion,
                )

            is Tracking -> state
        }

    /** The finger moved by ([dxDp], [dyDp]) since the last event. */
    fun drag(
        state: DockPullTransitionState,
        dxDp: Float,
        dyDp: Float,
    ): DockPullTransitionState =
        when (state) {
            is Tracking -> state.copy(dragXDp = state.dragXDp + dxDp, dragYDp = state.dragYDp + dyDp)
            else -> state
        }

    /** The finger let go, moving at ([velocityXDpPerSecond], [velocityYDpPerSecond]). */
    fun release(
        state: DockPullTransitionState,
        velocityXDpPerSecond: Float = 0f,
        velocityYDpPerSecond: Float = 0f,
    ): DockPullTransitionState =
        when (state) {
            is Tracking ->
                state.settling(
                    thresholds.outcomeFor(
                        progress = state.progress,
                        gestureDistanceDp = state.gestureDistanceDp,
                        velocityDpPerSecond = state.direction.along(velocityXDpPerSecond, velocityYDpPerSecond),
                    ),
                )

            else -> state
        }

    /** The gesture was taken away (pointer cancelled, another handler claimed it): spring back. */
    fun cancel(state: DockPullTransitionState): DockPullTransitionState =
        when (state) {
            is Tracking -> state.settling(SettleOutcome.CANCEL)
            else -> state
        }

    /** The settle animation reached [progress]. */
    fun settleProgress(
        state: DockPullTransitionState,
        progress: Float,
    ): DockPullTransitionState =
        when (state) {
            is Settling -> state.copy(progress = progress.coerceIn(0f, 1f))
            else -> state
        }

    /** The settle animation finished: the settle's end surface is now on screen. */
    fun finish(state: DockPullTransitionState): DockPullTransitionState =
        when (state) {
            is Settling -> Idle(surface = state.endSurface, reducedMotion = state.reducedMotion)
            else -> state
        }

    /** [state] with the reduced-motion preference changed, e.g. when the system setting changes. */
    fun withReducedMotion(
        state: DockPullTransitionState,
        reducedMotion: Boolean,
    ): DockPullTransitionState =
        when (state) {
            is Idle -> state.copy(reducedMotion = reducedMotion)
            is Tracking -> state.copy(reducedMotion = reducedMotion)
            is Settling -> state.copy(reducedMotion = reducedMotion)
        }

    private fun Tracking.settling(outcome: SettleOutcome): Settling =
        Settling(
            from = from,
            to = to,
            direction = direction,
            travelDp = travelDp,
            outcome = outcome,
            releaseProgress = progress,
            reducedMotion = reducedMotion,
        )
}

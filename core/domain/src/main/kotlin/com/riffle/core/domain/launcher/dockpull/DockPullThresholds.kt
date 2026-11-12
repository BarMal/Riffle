package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.gestures.GestureThresholds
import kotlin.math.abs

/** How a released pull ends. */
enum class SettleOutcome {
    /** The pull goes through: the Stream or the Library opens, or the Stream closes. */
    COMMIT,

    /** The pull is abandoned: everything springs back to where it was. */
    CANCEL,
}

/**
 * When a released Dock pull commits, in density-independent units (values in [GestureThresholds]).
 *
 * A release commits when the pull has come [commitFraction] of its travel, or when it is flung toward the interior at
 * [commitVelocityDpPerSecond] or faster. A fling back toward the edge at that speed cancels, however far the pull had
 * come. Speed only counts once the gesture has moved [minimumFlingDistanceDp] along the pull, so a tap or a twitch on
 * the Dock never commits.
 */
data class DockPullThresholds(
    val commitFraction: Float = GestureThresholds.DOCK_PULL_COMMIT_FRACTION,
    val commitVelocityDpPerSecond: Float = GestureThresholds.DOCK_PULL_COMMIT_VELOCITY_DP_PER_SECOND,
    val minimumFlingDistanceDp: Float = GestureThresholds.DOCK_PULL_MINIMUM_FLING_DISTANCE_DP,
) {
    init {
        require(commitFraction > 0f && commitFraction < 1f) { "Commit fraction must be in (0, 1): $commitFraction" }
        require(commitVelocityDpPerSecond > 0f) { "Commit velocity must be positive: $commitVelocityDpPerSecond" }
        require(minimumFlingDistanceDp >= 0f) { "Minimum fling distance must be >= 0: $minimumFlingDistanceDp" }
    }

    /**
     * How a pull released at [progress] ends, having moved [gestureDistanceDp] along the pull in this gesture and
     * moving at [velocityDpPerSecond] along it (positive toward the interior).
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
}

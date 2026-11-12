package com.riffle.core.domain.launcher.split

import com.riffle.core.domain.launcher.stream.StreamSplit
import kotlin.math.abs

/** Where a divider drag or a Dock pull ends: a mode, and for a split the Stream's share of the room. */
data class SplitTarget(
    val mode: SplitMode,
    val share: Float,
)

/**
 * How a released divider or Dock pull settles to one of the three states, by where it stopped and how fast it was
 * going. Pure; the UI animates to the answer.
 *
 * The Stream's *reveal* runs 0 (Home only), [SPLIT_ANCHOR] (a split) to 1 (Stream only). A release is projected a
 * short way along its velocity ([PROJECTION_SECONDS]) and takes the nearest of the three; so a hard fling goes the
 * whole way, and stopping partway leaves the split. Speed only counts once the gesture has moved
 * [MIN_FLING_DISTANCE_DP], so a tap or a twitch never moves anything.
 */
object SplitSettle {
    /** The reveal at which the Stream and Home share the screen. */
    const val SPLIT_ANCHOR = 0.5f

    /** How far ahead of a release its velocity is projected. */
    const val PROJECTION_SECONDS = 0.15f

    const val MIN_FLING_DISTANCE_DP = 8f

    /** The reveal at which each mode rests. */
    fun anchorOf(mode: SplitMode): Float =
        when (mode) {
            SplitMode.HOME_ONLY -> 0f
            SplitMode.SPLIT -> SPLIT_ANCHOR
            SplitMode.STREAM_ONLY -> 1f
        }

    /** The nearest mode to [reveal]; a split is not offered where there is no room for one. */
    fun nearestMode(
        reveal: Float,
        splitAvailable: Boolean,
    ): SplitMode =
        when {
            !splitAvailable -> if (reveal < SPLIT_ANCHOR) SplitMode.HOME_ONLY else SplitMode.STREAM_ONLY
            reveal < SPLIT_ANCHOR / 2f -> SplitMode.HOME_ONLY
            reveal > (1f + SPLIT_ANCHOR) / 2f -> SplitMode.STREAM_ONLY
            else -> SplitMode.SPLIT
        }

    /**
     * A Dock pull released at [reveal], moving [revealVelocityPerSecond] (positive toward more Stream), having moved
     * [gestureDistanceDp] in this gesture. [startMode] is where the pull began: a gesture that never moved keeps it.
     */
    fun settlePull(
        reveal: Float,
        revealVelocityPerSecond: Float,
        gestureDistanceDp: Float,
        startMode: SplitMode,
        splitAvailable: Boolean,
    ): SplitMode {
        val moved = abs(gestureDistanceDp) >= MIN_FLING_DISTANCE_DP
        if (!moved && abs(reveal - anchorOf(startMode)) < TWITCH) return startMode
        val projected =
            if (moved) {
                reveal + revealVelocityPerSecond * PROJECTION_SECONDS
            } else {
                reveal
            }
        return nearestMode(projected.coerceIn(0f, 1f), splitAvailable)
    }

    /**
     * A divider released with the Stream at [share] of the room, moving [shareVelocityPerSecond] (positive toward a
     * bigger Stream). Past the split's limits the divider goes to Home only or Stream only; within them the split
     * rests where it was let go (carried a little by its velocity) and the hinge's magnet then applies.
     */
    fun settleDivider(
        window: SplitWindow,
        share: Float,
        shareVelocityPerSecond: Float,
    ): SplitTarget {
        val limits = SplitGeometry.limits(window)
        val projected = (share + shareVelocityPerSecond * PROJECTION_SECONDS).coerceIn(0f, 1f)
        val lower = limits.minShare / 2f
        val upper = (limits.maxShare + 1f) / 2f
        return when {
            !limits.splitAvailable ->
                SplitTarget(
                    if (projected < 0.5f) SplitMode.HOME_ONLY else SplitMode.STREAM_ONLY,
                    projected.roundToExtreme(),
                )
            projected < lower -> SplitTarget(SplitMode.HOME_ONLY, 0f)
            projected > upper -> SplitTarget(SplitMode.STREAM_ONLY, 1f)
            else -> SplitTarget(SplitMode.SPLIT, SplitGeometry.resolveShare(window, projected))
        }
    }

    private fun Float.roundToExtreme(): Float = if (this < 0.5f) 0f else 1f

    /** Share a keyboard or accessibility step moves the divider by. */
    const val KEYBOARD_STEP = 0.05f

    /** A step of the divider from [mode] at [share] in [direction] (+1 a bigger Stream, -1 a smaller one). */
    fun step(
        window: SplitWindow,
        mode: SplitMode,
        share: Float,
        direction: Int,
    ): SplitTarget {
        val limits = SplitGeometry.limits(window)
        if (!limits.splitAvailable) {
            return SplitTarget(
                if (direction > 0) SplitMode.STREAM_ONLY else SplitMode.HOME_ONLY,
                if (direction > 0) 1f else 0f,
            )
        }
        return when (mode) {
            SplitMode.HOME_ONLY ->
                if (direction > 0) {
                    SplitTarget(
                        SplitMode.SPLIT,
                        limits.minShare,
                    )
                } else {
                    SplitTarget(SplitMode.HOME_ONLY, 0f)
                }
            SplitMode.STREAM_ONLY ->
                if (direction < 0) {
                    SplitTarget(
                        SplitMode.SPLIT,
                        limits.maxShare,
                    )
                } else {
                    SplitTarget(SplitMode.STREAM_ONLY, 1f)
                }
            SplitMode.SPLIT -> {
                val next = share + direction * KEYBOARD_STEP
                when {
                    next < limits.minShare - EPSILON -> SplitTarget(SplitMode.HOME_ONLY, 0f)
                    next > limits.maxShare + EPSILON -> SplitTarget(SplitMode.STREAM_ONLY, 1f)
                    else -> SplitTarget(SplitMode.SPLIT, next.coerceIn(limits.minShare, limits.maxShare))
                }
            }
        }
    }

    private const val TWITCH = 0.02f
    private const val EPSILON = 0.0001f

    /** The Stream's share of a split for the splits fraction a layout stores, kept in range. */
    fun fractionOf(share: Float): Float = share.coerceIn(StreamSplit.MIN_FRACTION, StreamSplit.MAX_FRACTION)
}

/**
 * How a Dock pull on an expanded posture reveals the Stream (the phone's full-switch pull is `streamPullFrame`). The
 * pull's progress runs 0..1 along the pull axis as ever; the *reveal* maps it onto Home only .. split .. Stream only:
 * opening from Home only the reveal is the progress; closing from a mode that starts at reveal r0, it is r0 * (1 -
 * progress), so a pull down from Stream only can stop at a split.
 */
object SplitReveal {
    /** The reveal of a pull [progress] of the way, starting at [startReveal], opening or closing. */
    fun reveal(
        progress: Float,
        startReveal: Float,
        opening: Boolean,
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (opening) startReveal + (1f - startReveal) * p else startReveal * (1f - p)
    }

    /** The pull progress at which the reveal is [target]; the inverse of [reveal]. */
    fun progressFor(
        target: Float,
        startReveal: Float,
        opening: Boolean,
    ): Float {
        val value =
            if (opening) {
                if (startReveal >= 1f) 1f else (target - startReveal) / (1f - startReveal)
            } else {
                if (startReveal <= 0f) 1f else 1f - target / startReveal
            }
        return value.coerceIn(0f, 1f)
    }

    /** How fast the reveal moves for a pull moving [progressVelocityPerSecond] (progress per second). */
    fun revealVelocity(
        progressVelocityPerSecond: Float,
        startReveal: Float,
        opening: Boolean,
    ): Float = progressVelocityPerSecond * if (opening) 1f - startReveal else -startReveal

    /**
     * The Stream's share of the room for [reveal] and the share a split rests at ([splitShare]): grows from nothing to
     * [splitShare] over the first half, then on to everything.
     */
    fun streamShare(
        reveal: Float,
        splitShare: Float,
    ): Float {
        val r = reveal.coerceIn(0f, 1f)
        val half = SplitSettle.SPLIT_ANCHOR
        return if (r <= half) splitShare * (r / half) else splitShare + (1f - splitShare) * ((r - half) / (1f - half))
    }

    /** How the Stream pane arrives: [slideFraction] of the window's extent along the pull from its rest place. */
    data class Frame(
        val streamShare: Float,
        val slideFraction: Float,
        val streamAlpha: Float,
    )

    /**
     * The frame at [reveal]: the Stream slides in over the first half (from the Dock's edge when opening, out toward
     * the interior when closing, in the pull's direction) while it widens, and rests thereafter. Under reduced motion
     * it only fades.
     */
    fun frame(
        reveal: Float,
        splitShare: Float,
        opening: Boolean,
        reducedMotion: Boolean,
    ): Frame {
        val r = reveal.coerceIn(0f, 1f)
        val stage = (r / SplitSettle.SPLIT_ANCHOR).coerceIn(0f, 1f)
        val share = streamShare(r, splitShare)
        return if (reducedMotion) {
            Frame(share, 0f, stage)
        } else {
            Frame(share, (if (opening) -1f else 1f) * (1f - stage) + 0f, 1f)
        }
    }
}

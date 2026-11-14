package com.riffle.core.domain.launcher.dock

import kotlin.math.abs
import kotlin.math.exp

/**
 * Closing is the opening played backwards in time (ADR 0003, Amendment 3, Motion): the opening is simulated once with
 * the same springs, then its samples are played last to first. So the cards start slowly and gather speed into the
 * Dock, the card that came out last goes back in first, and the Dock's bounce runs in reverse and ends in the dip; then
 * the Dock rises to rest while Home fades back in.
 *
 * Pure: [frameAt] is a function of the time since the closing started, so it is tested without a clock.
 */
class DockClosingPlan private constructor(
    /** The opening's samples, in the order the opening produced them (first = the press's own end, last = at rest). */
    val opening: List<DockMotionFrame>,
    private val tuning: DockMotionTuning,
) {
    private val count: Int get() = opening.size

    /** How long the reversed bounce lasts, in seconds. */
    val reversedSeconds: Float get() = count.toFloat() / DockMotionConstants.TRAJECTORY_HZ

    /** How long the Dock's rise to rest, with Home fading in, lasts. */
    val riseSeconds: Float get() = DockMotionConstants.CLOSE_RISE_SECONDS * tuning.timeScale

    /** The whole closing, in seconds. */
    val durationSeconds: Float get() = reversedSeconds + riseSeconds

    /** Whether a closing that joined at [join] and has run [elapsedSeconds] since is over. */
    fun isFinished(
        elapsedSeconds: Float,
        join: DockClosingJoin = DockClosingJoin.START,
    ): Boolean = join.timeSeconds + elapsedSeconds >= durationSeconds

    /**
     * The frame [elapsedSeconds] into a closing that joined the playback at [join] (the start, unless a finger caught
     * the Stream mid-way). The dip the Stream was showing when it joined dies away over
     * [DockMotionConstants.CLOSE_CARRY_SECONDS] rather than snapping.
     */
    fun frameAt(
        elapsedSeconds: Float,
        join: DockClosingJoin = DockClosingJoin.START,
    ): DockMotionFrame {
        val elapsed = elapsedSeconds.coerceAtLeast(0f)
        val base = sample(join.timeSeconds + elapsed)
        if (join.carriedDip == 0f) return base
        val carry = join.carriedDip * exp(-elapsed / (DockMotionConstants.CLOSE_CARRY_SECONDS * tuning.timeScale))
        return DockMotionFrame(base.p, base.dip + carry)
    }

    /**
     * Where to join the closing for a Stream caught at [frame] (an opening or a closing a finger interrupted): the time
     * at which the reversed playback is at the same progress, and the dip that has to be carried so nothing jumps.
     */
    fun joinAt(frame: DockMotionFrame): DockClosingJoin {
        val time = timeForProgress(frame.p)
        return DockClosingJoin(time, carriedDip = frame.dip - sample(time).dip)
    }

    /** The playback [t] seconds in, with nothing carried. */
    private fun sample(t: Float): DockMotionFrame {
        val k = count - 1 - (t.coerceAtLeast(0f) * DockMotionConstants.TRAJECTORY_HZ).toInt()
        if (k >= 0) return opening[k]
        val u = ((t - reversedSeconds) / riseSeconds).coerceIn(0f, 1f)
        val rest = 1f - smoothstep(u)
        return DockMotionFrame(p = opening.first().p * rest, dip = opening.first().dip * rest)
    }

    private fun timeForProgress(p: Float): Float =
        when {
            p >= opening.last().p -> 0f
            p >= opening.first().p -> {
                // The reversed playback runs from the last sample to the first; progress only falls along it.
                val index = (count - 1 downTo 0).first { opening[it].p <= p }
                (count - 1 - index).toFloat() / DockMotionConstants.TRAJECTORY_HZ
            }
            else -> reversedSeconds + riseFractionFor(p) * riseSeconds
        }

    /** How far through the final rise the smoothstep has taken progress down to [p] (below the press's own end). */
    private fun riseFractionFor(p: Float): Float {
        val target = 1f - p / opening.first().p
        var low = 0f
        var high = 1f
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2f
            if (smoothstep(mid) < target) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    companion object {
        private const val BISECTION_STEPS = 24

        /** The closing for [tuning]: the opening simulated from the press's end to rest, without its still tail. */
        fun of(tuning: DockMotionTuning = DockMotionTuning()): DockClosingPlan {
            val start =
                DockSpringState(
                    p = DockMotionConstants.FAN_START,
                    dip = DockMotionConstants.DIP_MAX_DP,
                    dipVelocity = DockMotionConstants.OPEN_DIP_VELOCITY,
                )
            val samples = ArrayList<DockMotionFrame>()
            samples += DockMotionFrame(start.p, start.dip)
            var state = start
            val step = 1f / DockMotionConstants.TRAJECTORY_HZ
            while (samples.size < DockMotionConstants.TRAJECTORY_MAX_SAMPLES) {
                state = DockSpring.advance(state, target = 1f, seconds = step, tuning = tuning)
                samples += DockMotionFrame(state.p, state.dip)
            }
            var n = samples.size
            while (n > 1 && isStill(samples[n - 1])) n--
            return DockClosingPlan(samples.subList(0, n).toList(), tuning)
        }

        private fun isStill(frame: DockMotionFrame): Boolean =
            abs(frame.p - 1f) < DockMotionConstants.TRAJECTORY_TAIL_PROGRESS &&
                abs(frame.dip) < DockMotionConstants.TRAJECTORY_TAIL_DIP_DP

        private fun smoothstep(u: Float): Float = u * u * (3f - 2f * u)
    }
}

/** Where a closing is joined: [timeSeconds] into the playback, with [carriedDip] fading out from there. */
data class DockClosingJoin(
    val timeSeconds: Float = 0f,
    val carriedDip: Float = 0f,
) {
    companion object {
        /** From the start of the playback with nothing carried: a closing from the open Stream at rest. */
        val START = DockClosingJoin()
    }
}

package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import kotlin.math.abs

/*
 * How an open card grows and where it sits (ADR 0003, Amendment 1, "Stream: browse, then open"; Amendment 3, "Motion"),
 * as pure code. The card grows in place from its browse size to its full content as a progress from 0 to 1, driven by a
 * critically damped spring like the rest of the motion; its constants live in [DockMotionConstants] and its frequency
 * in [DockMotionTuning]. Under reduced motion there is no growth: the card is there at once and fades in.
 */

/** The open card's growth at an instant: [progress] from 0 (browse size) to 1 (full), and its [velocity]. */
data class OpenCardSpringState(
    val progress: Float,
    val velocity: Float = 0f,
)

object OpenCardSpring {
    /** [state] advanced by [seconds] toward [target] (0 or 1), in fixed steps: the same path at any frame rate. */
    fun advance(
        state: OpenCardSpringState,
        target: Float,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): OpenCardSpringState {
        var current = state
        var remaining = seconds.coerceIn(0f, DockMotionConstants.MAX_FRAME_SECONDS)
        while (remaining > 0f) {
            val step = minOf(remaining, DockMotionConstants.STEP_SECONDS)
            current = step(current, target, step, tuning)
            remaining -= step
        }
        return current
    }

    /** One fixed step of [seconds]. Critically damped: it never crosses its rest, so it never swings past it. */
    fun step(
        state: OpenCardSpringState,
        target: Float,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): OpenCardSpringState {
        val omega = tuning.openCardOmega
        val velocity =
            state.velocity + (-omega * omega * (state.progress - target) - 2f * omega * state.velocity) * seconds
        val progress = state.progress + velocity * seconds
        if ((state.progress - target) * (progress - target) < 0f) return OpenCardSpringState(target, 0f)
        return OpenCardSpringState(progress, velocity)
    }

    fun isSettled(
        state: OpenCardSpringState,
        target: Float,
    ): Boolean =
        abs(state.progress - target) < DockMotionConstants.OPEN_CARD_SETTLED_PROGRESS &&
            abs(state.velocity) < DockMotionConstants.OPEN_CARD_SETTLED_VELOCITY

    /** Reduced motion: the card's opacity [elapsedSeconds] into the short fade that replaces the growth. */
    fun reducedFadeAlpha(elapsedSeconds: Float): Float {
        val fraction = (elapsedSeconds / DockMotionConstants.REDUCED_FADE_SECONDS).coerceIn(0f, 1f)
        val from = DockMotionConstants.OPEN_CARD_REDUCED_FADE_FROM
        return from + (1f - from) * fraction
    }
}

/** Where an open card sits in the area the Stream has, and how tall it is. Pure geometry in px. */
object OpenCardLayout {
    /** What the layout decides for one frame: the card's top edge and its visible height. */
    data class Frame(
        val topPx: Float,
        val heightPx: Float,
    )

    /**
     * The open card at [progress]. [areaHeightPx] is the Stream's area down to the top of the Dock's room;
     * [bottomInsetPx] is the gap left above the Dock; [browseHeightPx] is the card's browse height and [fullHeightPx]
     * its content's (already held to the room there is); [stripsPx] is the room the peeking cards above it take.
     * The card grows in place: at 0 it is exactly where its browse card stands (just above the Dock), at 1 where
     * [placement] puts it, never above the strips and never below the Dock's gap.
     */
    fun frame(
        progress: Float,
        areaHeightPx: Float,
        bottomInsetPx: Float,
        browseHeightPx: Float,
        fullHeightPx: Float,
        stripsPx: Float,
        placement: OpenCardPlacement,
    ): Frame {
        val p = progress.coerceIn(0f, 1f)
        val browse = browseHeightPx.coerceAtMost(fullHeightPx)
        val bottom = areaHeightPx - bottomInsetPx
        val room = (bottom - stripsPx).coerceAtLeast(0f)
        val full = fullHeightPx.coerceAtMost(room)
        val height = browse + (full - browse) * p
        val browseTop = bottom - browse
        val openTop =
            when (StreamCardActions.effectiveCardPlacement(placement)) {
                OpenCardPlacement.CENTRE -> stripsPx + (room - full) / 2f
                else -> bottom - full
            }
        return Frame(topPx = browseTop + (openTop - browseTop) * p, heightPx = height)
    }

    /** The room the strips of the cards behind take above the open card: a pitch each, at most the visible peeks. */
    fun stripsPx(
        cardsBehind: Int,
        pitchPx: Float,
    ): Float = cardsBehind.coerceIn(0, StreamCardStackSpec.VISIBLE_PEEKS) * pitchPx
}

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
     * its content's (already held to the room there is); [stripsPx] is the room the cards behind it take above it as
     * strips and [belowPx] the room the cards in front of it take below it.
     *
     * The card grows from exactly where its browse card stands: its bottom at 0 is [restBottomPx] (the collapsed
     * stack's bottom edge, which may stand above the area's bottom by what the page keeps under the stack) and its
     * height is [browseHeightPx]. At 1 it stands where [placement] puts it, never above the strips and never below
     * the room under it ([belowPx], the Dock's gap). It is never shorter than its browse card ([fullHeightPx] is
     * raised to it), so a card with little to say keeps its height and one with a lot of content takes all the room
     * there is. Top, bottom and height are each linear in [progress], so they move one way only.
     */
    @Suppress("LongParameterList")
    fun frame(
        progress: Float,
        areaHeightPx: Float,
        bottomInsetPx: Float,
        browseHeightPx: Float,
        fullHeightPx: Float,
        stripsPx: Float,
        placement: OpenCardPlacement,
        restBottomPx: Float = areaHeightPx - bottomInsetPx,
        belowPx: Float = 0f,
    ): Frame {
        val p = progress.coerceIn(0f, 1f)
        val bottom = areaHeightPx - bottomInsetPx - belowPx
        val room = (bottom - stripsPx).coerceAtLeast(0f)
        val browse = browseHeightPx.coerceAtMost(room)
        val full = maxOf(fullHeightPx.coerceAtMost(room), browse)
        val height = browse + (full - browse) * p
        val browseTop = restBottomPx - browse
        val openTop =
            when (placement) {
                OpenCardPlacement.CENTRE -> stripsPx + (room - full) / 2f
                OpenCardPlacement.ABOVE_DOCK -> bottom - full
            }
        return Frame(topPx = browseTop + (openTop - browseTop) * p, heightPx = height)
    }

    /**
     * Whether [wantedStripsPx] of strips (the cards behind, peeking out above the open card) leave an open card at
     * least [minCardPx] of room in an area [areaHeightPx] tall with [bottomInsetPx] kept under it. With the keyboard
     * up, or in a short window, they do not: the strips are then not drawn at all, so they never overlap the open card.
     */
    fun stripsFit(
        areaHeightPx: Float,
        bottomInsetPx: Float,
        wantedStripsPx: Float,
        minCardPx: Float,
    ): Boolean = areaHeightPx - bottomInsetPx - wantedStripsPx >= minCardPx

    /** The room the strips of the cards behind take above the open card: a pitch each, at most the visible peeks. */
    fun stripsPx(
        cardsBehind: Int,
        pitchPx: Float,
        visiblePeeks: Int = StreamCardStackSpec.VISIBLE_PEEKS,
    ): Float = cardsBehind.coerceIn(0, visiblePeeks) * pitchPx
}

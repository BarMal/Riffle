package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.dock.DockClosingJoin
import com.riffle.core.domain.launcher.dock.DockClosingPlan
import com.riffle.core.domain.launcher.dock.DockFan
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionFrame
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.dock.DockPressCurve
import com.riffle.core.domain.launcher.dock.DockReducedTimeline
import com.riffle.core.domain.launcher.dock.DockRelease
import com.riffle.core.domain.launcher.dock.DockSpring
import com.riffle.core.domain.launcher.dock.DockSpringState
import com.riffle.core.domain.launcher.dock.FanFrame
import com.riffle.core.domain.launcher.dock.HomeFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the Dock's motion is: nothing under way, a finger pressing the Dock, or a change running by itself. */
internal enum class DockMotionPhase { REST, PRESSING, RUNNING }

/**
 * The UI side of the Dock's trampoline and fan (ADR 0003, Amendment 3, Motion): carries the press (the dip under the
 * finger and Home's first fade), the release (the bounce, the cards fanning out, Home fading away) and the closing
 * (the opening played backwards in time), frame by frame, and hands what to draw to whoever draws it through
 * [progress] and [dip]. It decides nothing about the physics: the curves, springs, fan and the reversed trajectory are
 * the domain's (`dock/DockMotion.kt`, `dock/DockClosing.kt`).
 *
 * Interruptible by construction: a finger that claims the Dock as a press while a change is running takes over where it
 * stands (what was on screen hands over to the press over [DockMotionConstants.CATCH_BLEND_SECONDS], nothing snaps),
 * and whatever happens next, a release, a shallow press or another action, runs on from there. The shell is told of a
 * change when it starts opening and when it has finished closing, so the Stream is never shown half-open to it.
 *
 * Under reduced motion every run is [DockReducedTimeline]'s short cross-fade, with no dip and no fan.
 *
 * [progress] and [dip] are snapshot state: read them in draw and layout-free blocks, not composition. [running] and
 * [pressingFromOpen] change at the start and end of a run and are what composition may read.
 */
@Suppress("TooManyFunctions")
@Stable
internal class DockMotionState(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var closing: DockClosingPlan? = null
    private var closingTuning: DockMotionTuning? = null
    private var travelDp: Float = 0f
    private var caught: DockMotionFrame? = null
    private var blend: Float = 1f
    private var dimJob: Job? = null

    /** Where the motion is. */
    var phase: DockMotionPhase by mutableStateOf(DockMotionPhase.REST)
        private set

    /** Whether the running press started from the open Stream (then only the dock dips). */
    var pressingFromOpen: Boolean by mutableStateOf(false)
        private set

    /** How far Home has given way to the Stream, 0 to 1. Only meaningful while [running]. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** How far the dock is pressed toward its edge, in dp (negative while it has sprung past rest). */
    var dip: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Whether the press has turned into a selection (hold to choose): the Dock and Home stay where they were when
     * choosing began, and Home is dimmed further ([dim]) until it ends.
     */
    var choosing: Boolean by mutableStateOf(false)
        private set

    /** How much of Home's opacity is kept while choosing: 1 at all other times. Read it in a draw block. */
    var dim: Float by mutableFloatStateOf(1f)
        private set

    /** Whether under reduced motion: a plain cross-fade. Set when a press or a run starts. */
    var reduced: Boolean = false
        private set

    /** The speed, trampoline and magnet; the settings slice sets them. */
    var tuning: DockMotionTuning = DockMotionTuning()

    /** Whether the Stream is, or is on its way to being, open: what the shell has been told. */
    var logicalOpen: Boolean = false
        private set

    /** Called when an opening starts: the shell shows the Stream (it stands in for it until it agrees). */
    var onOpening: () -> Unit = {}

    /** Called when a closing has finished: the shell goes back to Home. */
    var onClosed: () -> Unit = {}

    /** Whether anything is under way: both surfaces are composed and drawn from [progress] while it is. */
    val running: Boolean get() = phase != DockMotionPhase.REST

    /** Whether a closing is under way (or a press that has caught one): the Stream is leaving. */
    var heading: Boolean? = null
        private set

    val isClosing: Boolean get() = phase == DockMotionPhase.RUNNING && heading == false

    /** Card [index] of the fan (or a one-piece page, index 0) as it is drawn now; null when nothing is under way. */
    fun fanFrame(index: Int): FanFrame? = if (running) DockFan.cardFrame(progress, index, reduced) else null

    /** Home as it is drawn now; null when nothing is under way. */
    fun homeFrame(): HomeFrame? =
        if (running) {
            DockFan.homeFrame(progress, reduced).let {
                    frame ->
                frame.copy(alpha = frame.alpha * dim)
            }
        } else {
            null
        }

    /** The Dock's resting state is [open]: the shell showed it (or an external change took it back to Home). */
    fun syncShell(open: Boolean) {
        if (running && logicalOpen != open) {
            job?.cancel()
            job = null
            rest(open)
        } else if (!running) {
            logicalOpen = open
        }
    }

    /** A finger claimed the Dock as a press, with the Stream [fromOpen] or not. Catches whatever was running. */
    fun beginPress(
        fromOpen: Boolean,
        reducedMotion: Boolean,
    ) {
        job?.cancel()
        job = null
        stopChoosing(animated = false)
        caught = if (running) DockMotionFrame(progress, dip) else null
        blend = if (caught == null) 1f else 0f
        reduced = reducedMotion
        logicalOpen = fromOpen
        heading = null
        pressingFromOpen = fromOpen
        travelDp = 0f
        phase = DockMotionPhase.PRESSING
        refreshPress()
        if (caught != null) job = scope.launch { blendIn() }
    }

    /**
     * The press turned into a selection: everything holds where it is (the Dock stays dipped, Home as faded) and
     * Home is dimmed further, at once (not animated, so reduced motion has nothing to skip).
     */
    fun beginChoosing() {
        if (phase != DockMotionPhase.PRESSING || choosing) return
        dimJob?.cancel()
        choosing = true
        dim = DockMotionConstants.CHOOSE_HOME_DIM
    }

    /**
     * The selection ended, whatever it led to: Home's extra dimming lets go, over a moment (at once under reduced
     * motion), so it does not blink as the fan or the bounce back starts.
     */
    fun endChoosing() = stopChoosing(animated = !reduced)

    private fun stopChoosing(animated: Boolean) {
        dimJob?.cancel()
        dimJob = null
        if (!choosing) {
            dim = 1f
            return
        }
        choosing = false
        if (!animated) {
            dim = 1f
            return
        }
        val from = dim
        dimJob =
            scope.launch {
                var start = -1L
                while (dim < 1f) {
                    val now = awaitFrameNanos()
                    if (start < 0) start = now
                    val t = ((now - start) / NANOS_PER_SECOND / DockMotionConstants.CHOOSE_DIM_RELEASE_SECONDS)
                    dim = from + (1f - from) * t.coerceIn(0f, 1f)
                }
            }
    }

    /** The finger is [travelDp] down. */
    fun updatePress(travelDp: Float) {
        if (phase != DockMotionPhase.PRESSING || choosing) return
        this.travelDp = travelDp
        refreshPress()
    }

    /** The press ended short of the threshold (or was taken away): the dock bounces back and nothing changes. */
    fun revert() {
        if (phase != DockMotionPhase.PRESSING) return
        val open = pressingFromOpen
        val target = if (open) 1f else 0f
        val from = DockMotionFrame(progress, dip)
        job?.cancel()
        if (reduced) {
            rest(open)
            return
        }
        phase = DockMotionPhase.RUNNING
        heading = open
        job =
            scope.launch {
                spring(DockRelease.shallow(from), target)
                rest(open)
            }
    }

    /**
     * Opens ([open]) or closes the Stream from wherever the Dock is: from a finger's press when one is running, else
     * from rest (the accessibility actions, a tap on a live app) or from the middle of another run.
     */
    fun run(
        open: Boolean,
        reducedMotion: Boolean,
        shown: Boolean,
    ) {
        val fromPress = phase == DockMotionPhase.PRESSING
        if (!running) logicalOpen = shown
        val from = if (running) DockMotionFrame(progress, dip) else DockMotionFrame(if (shown) 1f else 0f, 0f)
        job?.cancel()
        reduced = reducedMotion
        phase = DockMotionPhase.RUNNING
        heading = open
        pressingFromOpen = false
        progress = from.p
        dip = from.dip
        if (open && !logicalOpen) {
            logicalOpen = true
            onOpening()
        }
        job =
            scope.launch {
                when {
                    reduced -> fade(from.p, if (open) 1f else 0f)
                    open -> spring(if (fromPress) DockRelease.opening(from) else DockRelease.fromAction(from), 1f)
                    else -> close(from)
                }
                finish(open)
            }
    }

    /** Takes whatever is running to its end at once (a Library pull or another surface takes over). */
    fun finishNow() {
        if (!running) return
        job?.cancel()
        job = null
        val open = heading ?: pressingFromOpen
        if (phase == DockMotionPhase.PRESSING) rest(open) else finish(open)
    }

    private fun finish(open: Boolean) {
        rest(open)
        if (!open) onClosed()
    }

    private fun rest(open: Boolean) {
        stopChoosing(animated = false)
        logicalOpen = open
        heading = null
        caught = null
        phase = DockMotionPhase.REST
        pressingFromOpen = false
        progress = if (open) 1f else 0f
        dip = 0f
    }

    private fun refreshPress() {
        val target =
            if (reduced) {
                DockMotionFrame(if (pressingFromOpen) 1f else 0f, 0f)
            } else {
                DockPressCurve.frame(pressingFromOpen, travelDp)
            }
        val from = caught
        if (from == null) {
            progress = target.p
            dip = target.dip
        } else {
            progress = from.p + (target.p - from.p) * blend
            dip = from.dip + (target.dip - from.dip) * blend
        }
    }

    /** What was on screen when the finger caught the motion hands over to the press, so nothing snaps. */
    private suspend fun blendIn() {
        var start = -1L
        while (blend < 1f) {
            val now = awaitFrameNanos()
            if (start < 0) start = now
            blend = ((now - start) / NANOS_PER_SECOND / DockMotionConstants.CATCH_BLEND_SECONDS).coerceIn(0f, 1f)
            refreshPress()
        }
        caught = null
    }

    private suspend fun spring(
        start: DockSpringState,
        target: Float,
    ) {
        var state = start
        var last = -1L
        while (true) {
            val now = awaitFrameNanos()
            val seconds = if (last < 0) 0f else ((now - last) / NANOS_PER_SECOND)
            last = now
            state = DockSpring.advance(state, target, seconds, tuning)
            progress = state.p
            dip = state.dip
            if (DockSpring.isSettled(state, target)) break
        }
    }

    private suspend fun close(from: DockMotionFrame) {
        val plan = closingPlan()
        val join: DockClosingJoin = plan.joinAt(from)
        var start = -1L
        while (true) {
            val now = awaitFrameNanos()
            if (start < 0) start = now
            val elapsed = (now - start) / NANOS_PER_SECOND
            val frame = plan.frameAt(elapsed, join)
            progress = frame.p
            dip = frame.dip
            if (plan.isFinished(elapsed, join)) break
        }
    }

    private suspend fun fade(
        from: Float,
        to: Float,
    ) {
        var start = -1L
        while (true) {
            val now = awaitFrameNanos()
            if (start < 0) start = now
            val elapsed = (now - start) / NANOS_PER_SECOND
            progress = DockReducedTimeline.progress(from, to, elapsed)
            dip = 0f
            if (DockReducedTimeline.isFinished(elapsed)) break
        }
    }

    private fun closingPlan(): DockClosingPlan {
        val current = closing
        if (current != null && closingTuning == tuning) return current
        return DockClosingPlan.of(tuning).also {
            closing = it
            closingTuning = tuning
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

/** The next frame's time in nanoseconds: the platform's frame clock, which a test's clock drives too. */
private suspend fun awaitFrameNanos(): Long = androidx.compose.runtime.withFrameNanos { it }

@Composable
internal fun rememberDockMotionState(): DockMotionState {
    val scope: CoroutineScope = rememberCoroutineScope()
    return remember(scope) { DockMotionState(scope) }
}

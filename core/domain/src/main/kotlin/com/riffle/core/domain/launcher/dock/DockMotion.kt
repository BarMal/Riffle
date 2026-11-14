package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The numbers behind the Dock's trampoline and the cards' fan (ADR 0003, Amendment 3, Motion), named and grouped so the
 * settings slice can expose the three that are meant to be tuned ([DockMotionTuning]: speed, trampoline, magnet) and
 * nothing else needs to change. The values are the working prototype's (UX canvas, board M2).
 *
 * Distances are dp, times are seconds, progress ([DockSpringState.p]) runs from 0 (Home) to 1 (the Stream open).
 */
object DockMotionConstants {
    // The press: the Dock dips under the finger with rising resistance while Home starts to fade.

    /** The deepest the Dock dips while pressed; the dip approaches it and never reaches it. */
    const val DIP_MAX_DP: Float = 24f

    /** How quickly the resistance rises: the dip is `DIP_MAX_DP * (1 - exp(-travel / DIP_RESISTANCE_DP))`. */
    const val DIP_RESISTANCE_DP: Float = 60f

    /** How far Home has faded (progress) when the finger has pressed as far as the dip can go. */
    const val PRESS_FADE_MAX: Float = 0.3f

    // The spring that carries Home and the cards (critically damped: no bounce, a slight magnet).

    /** The default time scale: how long a change takes to settle, in milliseconds. */
    const val DEFAULT_SPEED_MILLIS: Int = 200

    /** A settle takes this many times the speed setting; the spring's frequency follows from it. */
    const val SETTLE_TIME_FACTOR: Float = 1.3f

    /** The undamped frequency is this many e-foldings over the settle time. */
    const val SETTLE_E_FOLDINGS: Float = 4f

    /** The default trampoline amount (0 = a dead dock, 1 = the springiest). */
    const val DEFAULT_TRAMPOLINE: Float = 0.5f

    /** The default magnet strength (0 = none, 1 = the strongest). */
    const val DEFAULT_MAGNET: Float = 0.4f

    /** The magnet works within this much progress of the spring's rest. */
    const val MAGNET_RANGE: Float = 0.25f

    /** At full magnet strength the pull at rest is this many times the spring's own. */
    const val MAGNET_GAIN: Float = 4f

    // The dock's own spring (under-damped, so it bounces: the trampoline).

    /** The dip's frequency, as a multiple of the main spring's. */
    const val DIP_FREQUENCY_FACTOR: Float = 1.6f

    /** The dip's damping ratio is `1 - trampoline * DIP_DAMPING_REDUCTION` (about 0.675 at the default). */
    const val DIP_DAMPING_REDUCTION: Float = 0.65f

    // What a release gives the dock, in dp per second (negative: away from the edge, back toward rest).

    /** A deep release that opens (and the opening the closing is played back from). */
    const val OPEN_DIP_VELOCITY: Float = -320f

    /** A shallow release that bounces back to rest. */
    const val SHALLOW_DIP_VELOCITY: Float = -120f

    /** The non-gesture route (the accessibility actions, a tap on a live app): a small dip and a kick. */
    const val ACTION_DIP_DP: Float = 12f
    const val ACTION_DIP_VELOCITY: Float = -260f

    // Where the spring is considered at rest.
    const val SETTLED_PROGRESS: Float = 0.002f
    const val SETTLED_PROGRESS_VELOCITY: Float = 0.02f
    const val SETTLED_DIP_DP: Float = 0.1f
    const val SETTLED_DIP_VELOCITY: Float = 2f

    /** The integration step. Fixed, so the same input gives the same trajectory whatever the frame rate. */
    const val STEP_SECONDS: Float = 1f / 240f

    /** The longest a single frame is allowed to advance a simulation (a stalled frame must not explode it). */
    const val MAX_FRAME_SECONDS: Float = 0.032f

    // The fan: the cards come out of the dock one after the other, front card first.

    /** Progress at which the fan starts (the press's own fade ends here) and the span it runs over. */
    const val FAN_START: Float = 0.3f
    const val FAN_SPAN: Float = 0.7f

    /** How much faster than the span a card runs, so the last card still lands before the end. */
    const val FAN_RATE: Float = 1.3f

    /** How much later each card starts than the one in front of it (in the fan's own units). */
    const val FAN_STAGGER: Float = 0.11f

    /** A card's opacity runs this much faster than its rise: it is solid before it has arrived. */
    const val FAN_ALPHA_RATE: Float = 1.6f

    /** A card starts this small and grows to full size. */
    const val FAN_SCALE_FROM: Float = 0.82f

    /** How far the front card rises from, and how much further each card behind it does. */
    const val FAN_RISE_FRONT_DP: Float = 230f
    const val FAN_RISE_STEP_DP: Float = 46f

    /** How many cards fan in sequence; any further card (or any one-piece page) uses the last one's timing. */
    const val FAN_CARDS: Int = 3

    /** Home scales down by this much as it fades. */
    const val HOME_SCALE_DOWN: Float = 0.04f

    // Closing is the opening played backwards in time.

    /** The opening is sampled this often, and its samples played last to first. */
    const val TRAJECTORY_HZ: Int = 120

    /** The longest opening simulated, in samples (three seconds). */
    const val TRAJECTORY_MAX_SAMPLES: Int = 360

    /** The settled tail of the opening is not played back: it stops once within these of rest. */
    const val TRAJECTORY_TAIL_PROGRESS: Float = 0.004f
    const val TRAJECTORY_TAIL_DIP_DP: Float = 0.3f

    /** After the reversed bounce the dock rises to rest while Home fades in, over this long (at the default speed). */
    const val CLOSE_RISE_SECONDS: Float = 0.11f

    /** A dip already showing when the closing starts (a press in the contextual mode) fades out over this long. */
    const val CLOSE_CARRY_SECONDS: Float = 0.06f

    // Reduced motion: a short cross-fade, nothing else (the design language's reduced-motion fade, 80 ms).
    const val REDUCED_FADE_SECONDS: Float = RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS / 1000f

    /** Catching an animation with a finger: how long what was on screen takes to hand over to the press. */
    const val CATCH_BLEND_SECONDS: Float = 0.1f
}

/**
 * The three motion settings the settings slice exposes (ADR 0003, Amendment 3: speed, trampoline, magnet) and what
 * follows from them. The defaults are the prototype's.
 */
data class DockMotionTuning(
    /** How long a change takes, in milliseconds (default 200). */
    val speedMillis: Int = DockMotionConstants.DEFAULT_SPEED_MILLIS,
    /** How much the dock bounces, 0 to 1 (default 0.5). */
    val trampoline: Float = DockMotionConstants.DEFAULT_TRAMPOLINE,
    /** How strongly the end of a movement is drawn in, 0 to 1 (default 0.4). */
    val magnet: Float = DockMotionConstants.DEFAULT_MAGNET,
) {
    private val speed: Int get() = speedMillis.coerceAtLeast(MIN_SPEED_MILLIS)

    /** The main spring's undamped frequency, in radians per second. */
    val omega: Float
        get() =
            DockMotionConstants.SETTLE_E_FOLDINGS /
                (speed * DockMotionConstants.SETTLE_TIME_FACTOR / MILLIS_PER_SECOND)

    /** How much stronger the main spring pulls at its rest than away from it (1 = not at all). */
    val magnetGain: Float get() = magnet.coerceIn(0f, 1f) * DockMotionConstants.MAGNET_GAIN

    /** The dip spring's frequency. */
    val dipOmega: Float get() = omega * DockMotionConstants.DIP_FREQUENCY_FACTOR

    /** The dip spring's damping ratio: below 1, so it overshoots. */
    val dipDamping: Float
        get() = 1f - trampoline.coerceIn(0f, 1f) * DockMotionConstants.DIP_DAMPING_REDUCTION

    /** How much longer or shorter than at the default speed a fixed-length phase of the closing runs. */
    val timeScale: Float get() = speed.toFloat() / DockMotionConstants.DEFAULT_SPEED_MILLIS

    private companion object {
        const val MIN_SPEED_MILLIS = 20
        const val MILLIS_PER_SECOND = 1000f
    }
}

/**
 * The Dock's motion at an instant: [p] is how far Home has given way to the Stream (0 to 1) and [dip] how far the dock
 * is pressed toward its edge, in dp (negative while it has sprung past rest). [pVelocity] and [dipVelocity] are their
 * rates per second, which a simulation carries on.
 */
data class DockSpringState(
    val p: Float = 0f,
    val pVelocity: Float = 0f,
    val dip: Float = 0f,
    val dipVelocity: Float = 0f,
)

/** What is drawn at an instant: Home's and the cards' progress and the dock's dip (dp). */
data class DockMotionFrame(
    val p: Float,
    val dip: Float,
)

/** The dip under the finger and the fade Home starts with while the Dock is pressed (before any release). */
object DockPressCurve {
    /** How far the dock dips once the finger has pressed [travelDp] toward the edge: rising resistance, below 24. */
    fun dipDp(travelDp: Float): Float = DockMotionConstants.DIP_MAX_DP * resistance(travelDp)

    /** How far Home has faded after [travelDp] of press, from Home: 0 to [DockMotionConstants.PRESS_FADE_MAX]. */
    fun fade(travelDp: Float): Float = DockMotionConstants.PRESS_FADE_MAX * resistance(travelDp)

    /**
     * The frame while the Dock is pressed [travelDp]: from Home ([fromOpen] false) the dock dips and Home starts to
     * fade; from the open Stream only the dock dips and the cards stay where they are until release.
     */
    fun frame(
        fromOpen: Boolean,
        travelDp: Float,
    ): DockMotionFrame = DockMotionFrame(p = if (fromOpen) 1f else fade(travelDp), dip = dipDp(travelDp))

    private fun resistance(travelDp: Float): Float =
        1f - exp(-travelDp.coerceAtLeast(0f) / DockMotionConstants.DIP_RESISTANCE_DP)
}

/** What a release starts the two springs from. */
object DockRelease {
    /** A deep press released from Home: the cards fan out (the spring runs to 1) and the dock bounces. */
    fun opening(frame: DockMotionFrame): DockSpringState =
        DockSpringState(p = frame.p, dip = frame.dip, dipVelocity = DockMotionConstants.OPEN_DIP_VELOCITY)

    /** A shallow press: the dock bounces back and the spring returns to where it was. */
    fun shallow(frame: DockMotionFrame): DockSpringState =
        DockSpringState(p = frame.p, dip = frame.dip, dipVelocity = DockMotionConstants.SHALLOW_DIP_VELOCITY)

    /** The non-gesture route from rest (an accessibility action): a small dip and a kick, then the same bounce. */
    fun fromAction(frame: DockMotionFrame): DockSpringState =
        DockSpringState(
            p = frame.p,
            dip = maxOf(frame.dip, DockMotionConstants.ACTION_DIP_DP),
            dipVelocity = DockMotionConstants.ACTION_DIP_VELOCITY,
        )
}

/**
 * The two springs, stepped. [DockSpringState.p] is critically damped with a magnet (no overshoot, ever: it carries
 * Home's opacity); [DockSpringState.dip] is under-damped (it overshoots past rest, the trampoline). Pure and
 * fixed-step, so a trajectory is the same whatever the frame rate.
 */
object DockSpring {
    /** [state] advanced by [seconds] toward progress [target]; the dip always settles on 0. */
    fun advance(
        state: DockSpringState,
        target: Float,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): DockSpringState {
        var current = state
        var remaining = seconds.coerceIn(0f, DockMotionConstants.MAX_FRAME_SECONDS)
        while (remaining > 0f) {
            val step = minOf(remaining, DockMotionConstants.STEP_SECONDS)
            current = step(current, target, step, tuning)
            remaining -= step
        }
        return current
    }

    /** One fixed step of [seconds]. */
    fun step(
        state: DockSpringState,
        target: Float,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): DockSpringState {
        val omega = tuning.omega
        val distance = abs(state.p - target)
        val near = maxOf(0f, 1f - distance / DockMotionConstants.MAGNET_RANGE)
        val pull = 1f + tuning.magnetGain * near
        val pVelocity =
            state.pVelocity +
                (-omega * omega * pull * (state.p - target) - 2f * omega * sqrt(pull) * state.pVelocity) * seconds
        var p = state.p + pVelocity * seconds
        var velocity = pVelocity
        // Critically damped means it never crosses its rest; the integrator must not either.
        if ((state.p - target) * (p - target) < 0f) {
            p = target
            velocity = 0f
        }
        val dipOmega = tuning.dipOmega
        val dipVelocity =
            state.dipVelocity +
                (-dipOmega * dipOmega * state.dip - 2f * tuning.dipDamping * dipOmega * state.dipVelocity) * seconds
        return DockSpringState(p, velocity, state.dip + dipVelocity * seconds, dipVelocity)
    }

    /** Whether both springs have come to rest at [target]. */
    fun isSettled(
        state: DockSpringState,
        target: Float,
    ): Boolean =
        abs(state.p - target) < DockMotionConstants.SETTLED_PROGRESS &&
            abs(state.pVelocity) < DockMotionConstants.SETTLED_PROGRESS_VELOCITY &&
            abs(state.dip) < DockMotionConstants.SETTLED_DIP_DP &&
            abs(state.dipVelocity) < DockMotionConstants.SETTLED_DIP_VELOCITY
}

/** How a card, or a page that rises as one piece, is drawn at an instant of the fan. */
data class FanFrame(
    /** Opacity, 0 to 1. */
    val alpha: Float,
    /** How much of its rise is still to go: 1 down in the dock, 0 in place. Multiply by [DockFan.riseDp]. */
    val rise: Float,
    /** Size, from [DockMotionConstants.FAN_SCALE_FROM] to 1. */
    val scale: Float,
)

/** How Home is drawn at an instant of the change. */
data class HomeFrame(
    val alpha: Float,
    val scale: Float,
)

/**
 * The cards fanning out of the Dock (ADR 0003, Amendment 3, Motion). Card 0 is the front card; each card behind starts
 * a little later. A page that is not a stack of cards (a List view, an empty or state page) rises as one piece with
 * card 0's timing. With [reduced] motion every frame is a plain cross-fade: no rise, no growth, no sequence.
 */
object DockFan {
    /** How far card [index] rises from, in dp: the front card least, those behind it a little more. */
    fun riseDp(index: Int): Float =
        DockMotionConstants.FAN_RISE_FRONT_DP + cardIndex(index) * DockMotionConstants.FAN_RISE_STEP_DP

    /** The card's own progress, 0 (in the dock) to 1 (in place), at overall progress [p]. */
    fun localProgress(
        p: Float,
        index: Int,
    ): Float {
        val q = (p - DockMotionConstants.FAN_START) / DockMotionConstants.FAN_SPAN
        return (q * DockMotionConstants.FAN_RATE - cardIndex(index) * DockMotionConstants.FAN_STAGGER).coerceIn(0f, 1f)
    }

    /** Card [index] at overall progress [p]. */
    fun cardFrame(
        p: Float,
        index: Int,
        reduced: Boolean = false,
    ): FanFrame {
        if (reduced) return FanFrame(alpha = p.coerceIn(0f, 1f), rise = 0f, scale = 1f)
        val t = localProgress(p, index)
        return FanFrame(
            alpha = (t * DockMotionConstants.FAN_ALPHA_RATE).coerceIn(0f, 1f),
            rise = 1f - t,
            scale = DockMotionConstants.FAN_SCALE_FROM + (1f - DockMotionConstants.FAN_SCALE_FROM) * t,
        )
    }

    /** A page that rises as one piece: the front card's timing. */
    fun pieceFrame(
        p: Float,
        reduced: Boolean = false,
    ): FanFrame = cardFrame(p, 0, reduced)

    /** Home at overall progress [p]: it fades out as the cards come in, with a slight scale-down. */
    fun homeFrame(
        p: Float,
        reduced: Boolean = false,
    ): HomeFrame {
        val c = p.coerceIn(0f, 1f)
        return HomeFrame(alpha = 1f - c, scale = if (reduced) 1f else 1f - DockMotionConstants.HOME_SCALE_DOWN * c)
    }

    private fun cardIndex(index: Int): Int = index.coerceIn(0, DockMotionConstants.FAN_CARDS - 1)
}

/** The change under reduced motion: one short, straight cross-fade, no dip and no bounce. */
object DockReducedTimeline {
    /** Progress [elapsedSeconds] into a fade from [from] to [to]. */
    fun progress(
        from: Float,
        to: Float,
        elapsedSeconds: Float,
    ): Float {
        val fraction = (elapsedSeconds / DockMotionConstants.REDUCED_FADE_SECONDS).coerceIn(0f, 1f)
        return from + (to - from) * fraction
    }

    /** Whether a fade [elapsedSeconds] in has run its course. */
    fun isFinished(elapsedSeconds: Float): Boolean = elapsedSeconds >= DockMotionConstants.REDUCED_FADE_SECONDS
}

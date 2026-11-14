package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning

/**
 * The motion the user can adjust (ADR 0003, Amendment 3, "Settings, for now"), stored as whole numbers so a stored
 * value is exact and a slider stops on it. The defaults are the prototype's constants, so nothing changes until one is
 * moved. Reduced motion overrides all of them (it is resolved elsewhere: [MotionSettings.reducedMotion]).
 */
data class DockMotionSettings(
    /** How long the Dock and the cards take to settle, in milliseconds. */
    val speedMillis: Int = DockMotionConstants.DEFAULT_SPEED_MILLIS,
    /** How much the Dock bounces after a press, in percent (0 is a dead Dock). */
    val trampolinePercent: Int = DEFAULT_TRAMPOLINE_PERCENT,
    /** How far a flung card stack coasts, in percent (100 is the prototype's). */
    val inertiaPercent: Int = DEFAULT_INERTIA_PERCENT,
    /** How strongly the end of a movement is drawn onto a card, in percent (0 is none). */
    val magnetPercent: Int = DEFAULT_MAGNET_PERCENT,
    /** How long a press is held still before it turns into choosing, in milliseconds. */
    val holdMillis: Int = DockMotionConstants.DEFAULT_HOLD_MILLIS,
    /** Whether a light tick sounds when a card lands and for each icon passed while choosing. */
    val ticks: Boolean = true,
) {
    /** The numbers the springs and the press read. */
    fun tuning(): DockMotionTuning =
        coerced().let { held ->
            DockMotionTuning(
                speedMillis = held.speedMillis,
                trampoline = held.trampolinePercent / PERCENT,
                magnet = held.magnetPercent / PERCENT,
                holdMillis = held.holdMillis,
                inertia = held.inertiaPercent / PERCENT,
            )
        }

    companion object {
        const val MAX_STRENGTH_PERCENT = 100
        const val MIN_SPEED_MILLIS = 100
        const val MAX_SPEED_MILLIS = 400
        const val SPEED_STEP_MILLIS = 10
        const val DEFAULT_TRAMPOLINE_PERCENT = 50
        const val DEFAULT_INERTIA_PERCENT = 100
        const val MAX_INERTIA_PERCENT = 250
        const val DEFAULT_MAGNET_PERCENT = 40
        const val MIN_HOLD_MILLIS = 100
        const val MAX_HOLD_MILLIS = 600
        const val HOLD_STEP_MILLIS = 20
        const val PERCENT_STEP = 10
        private const val PERCENT = 100f

        /** The stops the settings page offers for each value (the stored value may sit between two). */
        val SPEED_VALUES: IntProgression = MIN_SPEED_MILLIS..MAX_SPEED_MILLIS step SPEED_STEP_MILLIS
        val HOLD_VALUES: IntProgression = MIN_HOLD_MILLIS..MAX_HOLD_MILLIS step HOLD_STEP_MILLIS
        val STRENGTH_VALUES: IntProgression = 0..MAX_STRENGTH_PERCENT step PERCENT_STEP
        val INERTIA_VALUES: IntProgression = 0..MAX_INERTIA_PERCENT step PERCENT_STEP
    }
}

/** The settings held to their ranges (a stored or restored value out of range is clamped, never rejected). */
fun DockMotionSettings.coerced(): DockMotionSettings =
    copy(
        speedMillis = speedMillis.coerceIn(DockMotionSettings.MIN_SPEED_MILLIS, DockMotionSettings.MAX_SPEED_MILLIS),
        trampolinePercent = trampolinePercent.coerceIn(0, DockMotionSettings.MAX_STRENGTH_PERCENT),
        inertiaPercent = inertiaPercent.coerceIn(0, DockMotionSettings.MAX_INERTIA_PERCENT),
        magnetPercent = magnetPercent.coerceIn(0, DockMotionSettings.MAX_STRENGTH_PERCENT),
        holdMillis = holdMillis.coerceIn(DockMotionSettings.MIN_HOLD_MILLIS, DockMotionSettings.MAX_HOLD_MILLIS),
    )

/** Hints shown once. [dockPressSeen]: "press the Dock down to see what is live" has been shown and dismissed. */
data class HintSettings(
    val dockPressSeen: Boolean = false,
)

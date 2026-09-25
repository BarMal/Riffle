package com.riffle.core.domain.launcher.settings

/**
 * The launcher's reduced-motion setting. [SYSTEM] (the default) follows the platform animation state;
 * [ON] and [OFF] override it.
 */
enum class ReducedMotionPreference {
    SYSTEM,
    ON,
    OFF,
    ;

    /** Resolves the effective reduced-motion state for the platform's current [systemReducedMotion] state. */
    fun resolve(systemReducedMotion: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemReducedMotion
            ON -> true
            OFF -> false
        }

    fun next(): ReducedMotionPreference = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * Migrates the legacy boolean setting. It had no "follow the system" state, so an explicit `true`
         * stays [ON] while `false` (the old default, not a deliberate opt-out) becomes [SYSTEM].
         */
        fun fromLegacyReducedMotion(reducedMotion: Boolean): ReducedMotionPreference = if (reducedMotion) ON else SYSTEM
    }
}

/**
 * Platform animation signals, kept free of Android types so resolution can be tested without a device.
 *
 * [animatorDurationScale] mirrors `Settings.Global.ANIMATOR_DURATION_SCALE`; the accessibility
 * "Remove animations" switch sets it to 0.
 */
data class SystemAnimationSignals(
    val animatorDurationScale: Float = DEFAULT_ANIMATOR_DURATION_SCALE,
) {
    val reducedMotion: Boolean
        get() = animatorDurationScale <= 0f

    companion object {
        const val DEFAULT_ANIMATOR_DURATION_SCALE = 1f
    }
}

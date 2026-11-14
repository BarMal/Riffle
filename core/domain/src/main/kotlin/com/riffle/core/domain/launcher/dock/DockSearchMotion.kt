package com.riffle.core.domain.launcher.dock

import kotlin.math.exp

/**
 * What search opening from the Dock looks like (ADR 0003, Amendment 3, "everything from the Dock"): it rises from the
 * Dock's edge on the same calm spring the Dock's own change uses, at the Dock's opening speed, and closing is the same
 * motion the other way. Under reduced motion it is the 80 ms cross-fade and nothing moves.
 *
 * [progress] runs from 0 (not there) to 1 (open); [frame] says how it is drawn there.
 */
object DockSearchMotion {
    /** How far, in dp, the surface starts toward the Dock's edge from where it rests. */
    const val RISE_DP: Float = 36f

    /** The size it starts at, about the Dock's edge. */
    const val START_SCALE: Float = 0.94f

    /** Progress at which it is fully opaque: it fades in over the first part of the rise. */
    const val OPAQUE_AT: Float = 0.6f

    /** How close to its target the spring is taken to have arrived. */
    private const val ARRIVED: Float = 0.01f

    /**
     * Where a critically damped spring with frequency [omega] (radians per second) that left [from] at rest [elapsed]
     * seconds ago is on its way to [to]. It never overshoots, so a surface never rises past its place.
     */
    fun progress(
        from: Float,
        to: Float,
        elapsedSeconds: Float,
        omega: Float,
    ): Float {
        if (elapsedSeconds <= 0f) return from
        val x = omega * elapsedSeconds
        return to + (from - to) * (1f + x) * exp(-x)
    }

    /** Whether the spring from [from] to [to] has arrived [elapsedSeconds] in. */
    fun isArrived(
        from: Float,
        to: Float,
        elapsedSeconds: Float,
        omega: Float,
    ): Boolean = kotlin.math.abs(progress(from, to, elapsedSeconds, omega) - to) <= ARRIVED

    /** The progress of the reduced-motion fade from [from] to [to], [elapsedSeconds] in. */
    fun reducedProgress(
        from: Float,
        to: Float,
        elapsedSeconds: Float,
    ): Float = DockReducedTimeline.progress(from, to, elapsedSeconds)

    fun reducedIsArrived(elapsedSeconds: Float): Boolean = DockReducedTimeline.isFinished(elapsedSeconds)

    /** How the surface is drawn at [progress]; under [reducedMotion] only its alpha follows it. */
    fun frame(
        progress: Float,
        reducedMotion: Boolean,
    ): SearchFrame {
        val p = progress.coerceIn(0f, 1f)
        if (reducedMotion) return SearchFrame(alpha = p, riseDp = 0f, scale = 1f)
        return SearchFrame(
            alpha = (p / OPAQUE_AT).coerceIn(0f, 1f),
            riseDp = (1f - p) * RISE_DP,
            scale = START_SCALE + (1f - START_SCALE) * p,
        )
    }
}

/** One instant of the search surface: its opacity, how far toward the Dock's edge it still is (dp) and its scale. */
data class SearchFrame(
    val alpha: Float,
    val riseDp: Float,
    val scale: Float,
)

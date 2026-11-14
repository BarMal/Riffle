package com.riffle.core.domain.launcher.home

/**
 * How the Dock and the page handle come in when Home appears (cold start, and returning to Home after it was hidden):
 * a fade, with a small rise from the Dock's own edge (up from the bottom for a bottom Dock, inward from the side for a
 * side Dock). Only the two numbers below change over the entrance; the Dock and the handle are laid out where they
 * stand from the first frame. [progress] runs 0 (just before it starts) to 1 (arrived). Under reduced motion it is a
 * plain, short fade with no rise.
 */
object HomeEntrance {
    /** How far from its place, toward the Dock's edge, the Dock and the handle start, in dp. */
    const val RISE_DP = 14f

    /** The opacity at [progress]. */
    fun alpha(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** How far toward the Dock's edge the Dock and the handle are drawn at [progress], in dp: 0 once arrived. */
    fun offsetDp(
        progress: Float,
        reducedMotion: Boolean,
    ): Float = if (reducedMotion) 0f else (1f - progress.coerceIn(0f, 1f)) * RISE_DP
}

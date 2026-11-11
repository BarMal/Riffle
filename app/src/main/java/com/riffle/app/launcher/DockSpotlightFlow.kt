package com.riffle.app.launcher

import kotlin.math.abs
import kotlin.math.exp

/*
 * The spotlight's colour flow: rather than jumping when icons move, the glow on screen eases
 * toward where it should be, and spreads sideways as it goes, so colour reads as flowing along
 * the dock's rim. The same two ideas as the desktop plugin's edge borders -- a temporal blend
 * toward each new sample (its EMA_NEW) and an along-edge blur (its SMOOTH_RADIUS) -- applied to
 * the change rather than the glow itself, so a dock at rest shows exactly what its icons say.
 *
 * A profile is [DOCK_SPOTLIGHT_PROFILE_SAMPLES] premultiplied RGBA samples spread evenly along the
 * run: small enough that easing one costs nothing per frame, and it bakes straight into the edge
 * images (see `SpotlightBand.kt`) at that same resolution.
 */

/** Samples along the run; the desktop plugin's own per-edge resolution. */
internal const val DOCK_SPOTLIGHT_PROFILE_SAMPLES = 96

private const val CHANNELS = 4

/** A fresh, unlit profile. */
internal fun emptyDockSpotlightProfile(): FloatArray = FloatArray(DOCK_SPOTLIGHT_PROFILE_SAMPLES * CHANNELS)

/**
 * The profile [stops] describe: the colour along the run, unlit (all zero) where nothing glows.
 * Premultiplied, so easing and spreading toward or away from an unlit stretch never passes
 * through a darkened colour.
 */
internal fun dockSpotlightTargetProfile(stops: List<DockSpotlightStop>): FloatArray {
    val profile = emptyDockSpotlightProfile()
    val edgeStops = dockSpotlightEdgeStops(stops, alpha = 1f)
    if (edgeStops.isNotEmpty()) {
        for (sample in 0 until DOCK_SPOTLIGHT_PROFILE_SAMPLES) {
            val color = dockSpotlightColorAt(edgeStops, (sample + 0.5f) / DOCK_SPOTLIGHT_PROFILE_SAMPLES)
            val base = sample * CHANNELS
            profile[base] = color.red * color.alpha
            profile[base + 1] = color.green * color.alpha
            profile[base + 2] = color.blue * color.alpha
            profile[base + 3] = color.alpha
        }
    }
    return profile
}

/** Whether [profile] lights anything at all. */
internal fun dockSpotlightProfileIsLit(profile: FloatArray): Boolean =
    (0 until DOCK_SPOTLIGHT_PROFILE_SAMPLES).any { sample -> profile[sample * CHANNELS + 3] > SETTLED_EPSILON }

/**
 * Moves [shown] one frame of [dtSeconds] toward [target], in place, about 63% of the way per
 * [timeConstantSeconds] (the user's "Spotlight flow" setting), and reports whether it has
 * arrived (no visible gap left anywhere), in which case it is snapped exactly onto [target].
 *
 * Each sample moves by its remaining distance, partly its own and partly the average over its
 * neighbours (a box of [DOCK_SPOTLIGHT_DIFFUSION_RADIUS] either side): the neighbours' share is
 * what makes a change spread along the rim before settling. Keeping some of each sample's own
 * distance in the mix is what guarantees every sample still converges -- a pure neighbourhood
 * average can cancel out for a sample whose neighbours moved the other way. [scratch] is reused
 * for the spread distances, so a frame allocates nothing.
 */
internal fun stepDockSpotlightProfile(
    shown: FloatArray,
    target: FloatArray,
    dtSeconds: Float,
    timeConstantSeconds: Float,
    scratch: FloatArray,
): Boolean {
    val ease = 1f - exp(-dtSeconds.coerceAtLeast(0f) / timeConstantSeconds.coerceAtLeast(MIN_TIME_CONSTANT_SECONDS))
    val samples = DOCK_SPOTLIGHT_PROFILE_SAMPLES
    for (sample in 0 until samples) {
        val from = (sample - DOCK_SPOTLIGHT_DIFFUSION_RADIUS).coerceAtLeast(0)
        val to = (sample + DOCK_SPOTLIGHT_DIFFUSION_RADIUS).coerceAtMost(samples - 1)
        for (channel in 0 until CHANNELS) {
            var sum = 0f
            for (neighbour in from..to) {
                val index = neighbour * CHANNELS + channel
                sum += target[index] - shown[index]
            }
            val index = sample * CHANNELS + channel
            val own = target[index] - shown[index]
            scratch[index] = own + DOCK_SPOTLIGHT_DIFFUSION * (sum / (to - from + 1) - own)
        }
    }
    var largestGap = 0f
    for (sample in 0 until samples) {
        val base = sample * CHANNELS
        val alpha = (shown[base + 3] + ease * scratch[base + 3]).coerceIn(0f, 1f)
        shown[base + 3] = alpha
        for (channel in 0 until CHANNELS - 1) {
            // Premultiplied: no channel brighter than its own coverage.
            shown[base + channel] = (shown[base + channel] + ease * scratch[base + channel]).coerceIn(0f, alpha)
        }
        for (channel in 0 until CHANNELS) {
            largestGap = maxOf(largestGap, abs(target[base + channel] - shown[base + channel]))
        }
    }
    val settled = largestGap < SETTLED_EPSILON
    if (settled) target.copyInto(shown)
    return settled
}

/**
 * Hard ceiling on one settle, so the frame loop always ends -- an animation that never settles
 * would keep the dock redrawing for good. Scales with the chosen pace, as a slow flow legitimately
 * takes longer: a change settles in about 5.5 time constants (90% there after about 2), so 8 is
 * never normally reached.
 */
internal fun dockSpotlightMaxSettleSeconds(timeConstantSeconds: Float): Float =
    timeConstantSeconds * MAX_SETTLE_TIME_CONSTANTS

private const val MAX_SETTLE_TIME_CONSTANTS = 8f

/** Guards the easing against a zero time constant; an instant flow never steps at all. */
private const val MIN_TIME_CONSTANT_SECONDS = 0.001f

/** The longest frame step taken, so a stalled frame does not jump the glow. */
internal const val DOCK_SPOTLIGHT_MAX_FRAME_SECONDS = 0.05f

/** Neighbours either side a change spreads to, in samples (the desktop plugin's own radius). */
private const val DOCK_SPOTLIGHT_DIFFUSION_RADIUS = 3

/**
 * How much of each step comes from the neighbourhood rather than the sample itself. Half and half
 * spreads a change visibly while every sample still settles in under two seconds.
 */
private const val DOCK_SPOTLIGHT_DIFFUSION = 0.5f

/**
 * Close enough to stop: the glow is drawn at 55% strength at most, so a gap this size is under one
 * 8-bit step on screen -- and every frame spent closing an invisible gap would be a redraw.
 */
private const val SETTLED_EPSILON = 1.5f / 255f

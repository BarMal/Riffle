package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Baking the dock spotlight's edges into images from its colour profile (see
 * `DockSpotlightFlow.kt`): the long edges along the run, the short ends from the colour the
 * profile carries out to each. See `DockSpotlight.kt`
 * for what the spotlight is and why it is baked rather than drawn with gradient shaders.
 */

/** One edge's glow, ready to draw: a small baked image stretched over the edge's band. */
internal class SpotlightBand(
    val image: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize,
)

/**
 * One long edge's glow, baked from [profile]: the colour along the run times [alpha] and an
 * inward fade across [depthPx], or null when it would not show. [atStart] picks the top
 * (horizontal run) or left (vertical run) edge.
 *
 * Baked on the CPU at the profile's own resolution -- [DOCK_SPOTLIGHT_PROFILE_SAMPLES] along the
 * run by [SPOTLIGHT_RAMP_TEXELS] across it, about 1,500 pixels -- and stretched over the band, so
 * the GPU draws one plain image: no gradient shader and no offscreen layer. Live gradient shaders
 * on the dock hung the software-rendered CI emulator, and an image this size costs next to
 * nothing to rebake on each frame the glow is flowing.
 */
@Suppress("LongParameterList")
internal fun CacheDrawScope.spotlightBand(
    profile: FloatArray,
    runsHorizontally: Boolean,
    atStart: Boolean,
    depthPx: Float,
    alpha: Float,
): SpotlightBand? {
    val runLength = if (runsHorizontally) size.width else size.height
    val crossLength = if (runsHorizontally) size.height else size.width
    val depth = ceil(depthPx.coerceAtMost(crossLength * SPOTLIGHT_MAX_DEPTH_FRACTION)).toInt()
    return if (depth <= 0 || runLength < 1f) {
        null
    } else {
        val alongPx = ceil(runLength).toInt()
        val edgeStart = if (atStart) 0 else floor(crossLength).toInt() - depth
        SpotlightBand(
            image = bakeSpotlightBand(profile, alpha, runsHorizontally, atStart),
            offset = if (runsHorizontally) IntOffset(0, edgeStart) else IntOffset(edgeStart, 0),
            size = if (runsHorizontally) IntSize(alongPx, depth) else IntSize(depth, alongPx),
        )
    }
}

/**
 * One short end's glow: the colour [profile] carries out to that end (the nearest icon's, or
 * nothing when that icon is unlit), fading inward along the run. Uniform across the dock's
 * thickness, so it bakes to a one-texel-thick ramp stretched across it.
 */
internal fun CacheDrawScope.spotlightEndBand(
    profile: FloatArray,
    runsHorizontally: Boolean,
    atRunStart: Boolean,
): SpotlightBand? {
    val sample = if (atRunStart) 0 else DOCK_SPOTLIGHT_PROFILE_SAMPLES - 1
    val coverage = profile[sample * CHANNELS + 3] * SPOTLIGHT_END_ALPHA
    val runLength = if (runsHorizontally) size.width else size.height
    val crossPx = ceil(if (runsHorizontally) size.height else size.width).toInt()
    val depth = ceil(SPOTLIGHT_END_DEPTH_DP.dp.toPx().coerceAtMost(runLength * SPOTLIGHT_MAX_DEPTH_FRACTION)).toInt()
    return if (coverage <= 0f || minOf(depth, crossPx) <= 0) {
        null
    } else {
        val start = if (atRunStart) 0 else floor(runLength).toInt() - depth
        val pixels =
            IntArray(SPOTLIGHT_RAMP_TEXELS) { texel ->
                // The outer end of the dock is fully lit, fading to nothing at the inner end.
                val fromOuter = if (atRunStart) texel else SPOTLIGHT_RAMP_TEXELS - 1 - texel
                profileArgb(profile, sample, SPOTLIGHT_END_ALPHA * rampFade(fromOuter))
            }
        val width = if (runsHorizontally) SPOTLIGHT_RAMP_TEXELS else 1
        val height = if (runsHorizontally) 1 else SPOTLIGHT_RAMP_TEXELS
        SpotlightBand(
            image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap(),
            offset = if (runsHorizontally) IntOffset(start, 0) else IntOffset(0, start),
            size = if (runsHorizontally) IntSize(depth, crossPx) else IntSize(crossPx, depth),
        )
    }
}

private fun bakeSpotlightBand(
    profile: FloatArray,
    alpha: Float,
    runsHorizontally: Boolean,
    atStart: Boolean,
): ImageBitmap {
    val along = DOCK_SPOTLIGHT_PROFILE_SAMPLES
    val width = if (runsHorizontally) along else SPOTLIGHT_RAMP_TEXELS
    val height = if (runsHorizontally) SPOTLIGHT_RAMP_TEXELS else along
    val pixels = IntArray(width * height)
    for (sample in 0 until along) {
        for (texel in 0 until SPOTLIGHT_RAMP_TEXELS) {
            // texel 0 is the outer edge, fading to nothing at the inner end of the band.
            val crossIndex = if (atStart) texel else SPOTLIGHT_RAMP_TEXELS - 1 - texel
            val index = if (runsHorizontally) crossIndex * width + sample else sample * width + crossIndex
            pixels[index] = profileArgb(profile, sample, alpha * rampFade(texel))
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Linear fade across the ramp: texel 0 (the edge) fully lit, the last nearly clear. */
private fun rampFade(texelFromEdge: Int): Float = 1f - (texelFromEdge + 0.5f) / SPOTLIGHT_RAMP_TEXELS

/**
 * [profile]'s colour at [sample] as an unpremultiplied ARGB pixel (what [Bitmap.createBitmap]
 * takes), its coverage scaled by [alpha].
 */
private fun profileArgb(
    profile: FloatArray,
    sample: Int,
    alpha: Float,
): Int {
    val base = sample * CHANNELS
    val coverage = profile[base + 3]
    return if (coverage <= 0f) {
        0
    } else {
        Color(
            red = (profile[base] / coverage).coerceIn(0f, 1f),
            green = (profile[base + 1] / coverage).coerceIn(0f, 1f),
            blue = (profile[base + 2] / coverage).coerceIn(0f, 1f),
            alpha = (coverage * alpha).coerceIn(0f, 1f),
        ).toArgb()
    }
}

/**
 * The glow's colour [position] (0..1) of the way along the run: linear between the two
 * [edgeStops] either side of it, in unpremultiplied colour -- what a linear gradient through the
 * same stops draws.
 */
internal fun dockSpotlightColorAt(
    edgeStops: Array<Pair<Float, Color>>,
    position: Float,
): Color {
    val after = edgeStops.indexOfFirst { (stop, _) -> stop >= position }
    return when {
        edgeStops.isEmpty() -> Color.Transparent
        after == 0 -> edgeStops.first().second
        after < 0 -> edgeStops.last().second
        else -> {
            val (fromPosition, from) = edgeStops[after - 1]
            val (toPosition, to) = edgeStops[after]
            val span = toPosition - fromPosition
            val t = if (span > 0f) (position - fromPosition) / span else 1f
            Color(
                red = from.red + (to.red - from.red) * t,
                green = from.green + (to.green - from.green) * t,
                blue = from.blue + (to.blue - from.blue) * t,
                alpha = from.alpha + (to.alpha - from.alpha) * t,
            )
        }
    }
}

// The short ends sit between the two long edges' strengths, so the rim reads as one continuous
// glow round the dock rather than two lit strips.
private const val SPOTLIGHT_END_DEPTH_DP = 8
private const val SPOTLIGHT_END_ALPHA = 0.42f

/** Texels across a band's depth; stretched, a linear fade needs no more (the desktop plugin's RAMP_H). */
private const val SPOTLIGHT_RAMP_TEXELS = 16

/** RGBA, premultiplied, per profile sample. */
private const val CHANNELS = 4

/** Keeps the two edges' glows from meeting in the middle of a slim dock. */
private const val SPOTLIGHT_MAX_DEPTH_FRACTION = 0.3f

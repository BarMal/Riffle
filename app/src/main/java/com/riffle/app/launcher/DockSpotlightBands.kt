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
 * Baking the dock spotlight's edges into images: the long edges from the icon stops along the
 * run, the short ends from the colour each long edge carries out to it. See `DockSpotlight.kt`
 * for what the spotlight is and why it is baked rather than drawn with gradient shaders.
 */

/** One edge's glow, ready to draw: a small baked image stretched over the edge's band. */
internal class SpotlightBand(
    val image: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize,
)

/**
 * One edge's glow: the colour gradient along the run times an inward fade across [depthPx], or
 * null when there is nothing to draw. [atStart] picks the top (horizontal run) or left (vertical
 * run) edge.
 *
 * Baked pixel by pixel on the CPU into an image at a fraction of the run's resolution (the colour
 * changes slowly along it) and stretched back up, so the GPU draws one plain image -- no gradient
 * shader and no offscreen layer. Live gradient shaders on the dock hung the software-rendered CI
 * emulator; baking also keeps scrolling cheap, as only these few thousand pixels are redone.
 */
@Suppress("LongParameterList")
internal fun CacheDrawScope.spotlightBand(
    stops: List<DockSpotlightStop>,
    runsHorizontally: Boolean,
    atStart: Boolean,
    depthPx: Float,
    alpha: Float,
): SpotlightBand? {
    val edgeStops = dockSpotlightEdgeStops(stops, alpha)
    val runLength = if (runsHorizontally) size.width else size.height
    val crossLength = if (runsHorizontally) size.height else size.width
    val depth = ceil(depthPx.coerceAtMost(crossLength * SPOTLIGHT_MAX_DEPTH_FRACTION)).toInt()
    return if (edgeStops.isEmpty() || depth <= 0 || runLength < 1f) {
        null
    } else {
        val alongPx = ceil(runLength).toInt()
        val edgeStart = if (atStart) 0 else floor(crossLength).toInt() - depth
        SpotlightBand(
            image = bakeSpotlightBand(edgeStops, alongPx, depth, runsHorizontally, atStart),
            offset = if (runsHorizontally) IntOffset(0, edgeStart) else IntOffset(edgeStart, 0),
            size = if (runsHorizontally) IntSize(alongPx, depth) else IntSize(depth, alongPx),
        )
    }
}

/**
 * One short end's glow: the colour the long edges carry out to that end (the nearest icon's, or
 * nothing when that icon is unlit), fading inward along the run. Uniform across the dock's
 * thickness, so it bakes to a one-pixel ramp stretched across it.
 */
internal fun CacheDrawScope.spotlightEndBand(
    stops: List<DockSpotlightStop>,
    runsHorizontally: Boolean,
    atRunStart: Boolean,
): SpotlightBand? {
    val edgeStops = dockSpotlightEdgeStops(stops, SPOTLIGHT_END_ALPHA)
    val color = (if (atRunStart) edgeStops.firstOrNull() else edgeStops.lastOrNull())?.second
    val runLength = if (runsHorizontally) size.width else size.height
    val crossPx = ceil(if (runsHorizontally) size.height else size.width).toInt()
    val depth = ceil(SPOTLIGHT_END_DEPTH_DP.dp.toPx().coerceAtMost(runLength * SPOTLIGHT_MAX_DEPTH_FRACTION)).toInt()
    val litColor = color?.takeIf { it.alpha > 0f }
    return if (litColor == null || minOf(depth, crossPx) <= 0) {
        null
    } else {
        val start = if (atRunStart) 0 else floor(runLength).toInt() - depth
        val pixels =
            IntArray(depth) { index ->
                // The outer end of the dock is fully lit, fading to nothing at the inner end.
                val fromOuter = if (atRunStart) index else depth - 1 - index
                litColor.copy(alpha = litColor.alpha * (1f - (fromOuter + 0.5f) / depth)).toArgb()
            }
        val width = if (runsHorizontally) depth else 1
        val height = if (runsHorizontally) 1 else depth
        SpotlightBand(
            image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap(),
            offset = if (runsHorizontally) IntOffset(start, 0) else IntOffset(0, start),
            size = if (runsHorizontally) IntSize(depth, crossPx) else IntSize(crossPx, depth),
        )
    }
}

private fun bakeSpotlightBand(
    edgeStops: Array<Pair<Float, Color>>,
    alongPx: Int,
    depth: Int,
    runsHorizontally: Boolean,
    atStart: Boolean,
): ImageBitmap {
    val along = ceil(alongPx / SPOTLIGHT_BAKE_SCALE).toInt().coerceAtLeast(1)
    val width = if (runsHorizontally) along else depth
    val height = if (runsHorizontally) depth else along
    val pixels = IntArray(width * height)
    for (a in 0 until along) {
        val color = dockSpotlightColorAt(edgeStops, (a + 0.5f) / along)
        for (d in 0 until depth) {
            // d = 0 is the outer edge, fading to nothing at the inner end of the band.
            val fade = 1f - (d + 0.5f) / depth
            val crossIndex = if (atStart) d else depth - 1 - d
            val index = if (runsHorizontally) crossIndex * width + a else a * width + crossIndex
            pixels[index] = color.copy(alpha = color.alpha * fade).toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
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

/** The glow is baked at 1/4 of the run's resolution: its colour changes slowly along it. */
private const val SPOTLIGHT_BAKE_SCALE = 4f

/** Keeps the two edges' glows from meeting in the middle of a slim dock. */
private const val SPOTLIGHT_MAX_DEPTH_FRACTION = 0.3f

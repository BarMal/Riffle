package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/*
 * The dock spotlight's glow, baked from its colour profile (see `DockSpotlightFlow.kt`) into one
 * small image of the whole dock.
 *
 * The glow fades with distance from the dock's actual rounded outline, as the desktop plugin's
 * corner textures follow a window's rounding: on a pill-shaped dock it curves round the ends
 * instead of four straight bands meeting in a visible inner rectangle. How deep and how strong it
 * is blends by which way the nearest stretch of outline faces -- the inner long edge strongest,
 * the outer one weaker, the ends between -- and its colour is the profile's at that point along
 * the run, so the ends carry the nearest icon's colour.
 *
 * Baked on the CPU, at a coarse texel (the glow is soft) and stretched over the dock, so the GPU
 * draws one plain image: no gradient shader and no offscreen layer, which on the software-rendered
 * CI emulator hung the UI tests. The pixel buffer and bitmap are reused between bakes, so a frame
 * of the glow flowing allocates nothing.
 */

/** How deep and how strong the glow is along each kind of edge, in pixels and 0..1. */
internal data class DockSpotlightGlowEdges(
    val innerDepthPx: Float,
    val outerDepthPx: Float,
    val endDepthPx: Float,
    val innerAlpha: Float,
    val outerAlpha: Float,
    val endAlpha: Float,
)

/**
 * How lit the glow is (0..1, before the profile's own coverage) at a point [along] the run and
 * [across] it, in a dock [runLength] by [crossLength] with corners rounded to [cornerRadius]. The
 * across axis starts at the edge [innerAtStart] says is the inner one.
 *
 * The distance to the rounded outline is the exact rounded-rectangle distance; the edge kind is
 * weighted by the outline's direction at its nearest point (the squared components of its normal),
 * so a straight edge is purely itself and the round ends blend smoothly between them.
 */
@Suppress("LongParameterList")
internal fun dockSpotlightGlowAlpha(
    along: Float,
    across: Float,
    runLength: Float,
    crossLength: Float,
    cornerRadius: Float,
    innerAtStart: Boolean,
    edges: DockSpotlightGlowEdges,
): Float {
    val halfRun = runLength / 2f
    val halfCross = crossLength / 2f
    val radius = cornerRadius.coerceIn(0f, minOf(halfRun, halfCross))
    val fromCentreAlong = along - halfRun
    val fromCentreAcross = across - halfCross
    val overAlong = abs(fromCentreAlong) - (halfRun - radius)
    val overAcross = abs(fromCentreAcross) - (halfCross - radius)

    // Distance inside the outline, and the outline's (squared) facing at its nearest point.
    val inCorner = overAlong > 0f && overAcross > 0f
    val cornerDistance = if (inCorner) sqrt(overAlong * overAlong + overAcross * overAcross) else 0f
    val inside = if (inCorner) radius - cornerDistance else radius - maxOf(overAlong, overAcross)
    val endWeight =
        when {
            inCorner && cornerDistance > 0f -> (overAlong / cornerDistance).let { it * it }
            overAlong > overAcross -> 1f
            else -> 0f
        }
    val acrossWeight = 1f - endWeight
    val facesInner = (fromCentreAcross < 0f) == innerAtStart

    val sideDepth = if (facesInner) edges.innerDepthPx else edges.outerDepthPx
    val sideAlpha = if (facesInner) edges.innerAlpha else edges.outerAlpha
    val depth = endWeight * edges.endDepthPx + acrossWeight * sideDepth
    val alpha = endWeight * edges.endAlpha + acrossWeight * sideAlpha
    return if (depth <= 0f) 0f else alpha * (1f - inside.coerceAtLeast(0f) / depth).coerceIn(0f, 1f)
}

/** Bakes the glow into one reusable image; one per dock strip, used on the main thread only. */
internal class DockSpotlightGlowBaker {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)

    /**
     * The glow for [profile] over a dock [runPx] by [crossPx] (in the run's own orientation:
     * [runsHorizontally] lays it out on screen), at [texelPx] per texel.
     */
    @Suppress("LongParameterList")
    fun bake(
        profile: FloatArray,
        runPx: Float,
        crossPx: Float,
        cornerRadiusPx: Float,
        innerAtStart: Boolean,
        runsHorizontally: Boolean,
        edges: DockSpotlightGlowEdges,
        texelPx: Float,
    ): ImageBitmap {
        val alongTexels = ceil(runPx / texelPx).toInt().coerceAtLeast(1)
        val acrossTexels = ceil(crossPx / texelPx).toInt().coerceAtLeast(1)
        val width = if (runsHorizontally) alongTexels else acrossTexels
        val height = if (runsHorizontally) acrossTexels else alongTexels
        val target = reusableBitmap(width, height)
        for (a in 0 until alongTexels) {
            val along = (a + 0.5f) * runPx / alongTexels
            val color = dockSpotlightProfileColorAt(profile, along / runPx)
            for (c in 0 until acrossTexels) {
                val across = (c + 0.5f) * crossPx / acrossTexels
                val lit = dockSpotlightGlowAlpha(along, across, runPx, crossPx, cornerRadiusPx, innerAtStart, edges)
                val index = if (runsHorizontally) c * width + a else a * width + c
                pixels[index] = glowPixel(color, lit)
            }
        }
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        return target.asImageBitmap()
    }

    private fun reusableBitmap(
        width: Int,
        height: Int,
    ): Bitmap {
        val existing = bitmap
        return if (existing != null && existing.width == width && existing.height == height) {
            existing
        } else {
            pixels = IntArray(width * height)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap = it }
        }
    }
}

/** [color] at [lit] strength, as the unpremultiplied ARGB pixel [Bitmap.setPixels] takes. */
private fun glowPixel(
    color: Color,
    lit: Float,
): Int = if (lit <= 0f || color.alpha <= 0f) 0 else color.copy(alpha = (color.alpha * lit).coerceIn(0f, 1f)).toArgb()

/**
 * [profile]'s colour [position] (0..1) of the way along the run, unpremultiplied: linear between
 * the two samples either side of it.
 */
internal fun dockSpotlightProfileColorAt(
    profile: FloatArray,
    position: Float,
): Color {
    val samples = DOCK_SPOTLIGHT_PROFILE_SAMPLES
    val exact = (position * samples - 0.5f).coerceIn(0f, samples - 1f)
    val low = floor(exact).toInt()
    val high = minOf(low + 1, samples - 1)
    val t = exact - low
    val channel = { offset: Int ->
        profile[low * CHANNELS + offset] + (profile[high * CHANNELS + offset] - profile[low * CHANNELS + offset]) * t
    }
    val coverage = channel(ALPHA)
    return if (coverage <= 0f) {
        Color.Transparent
    } else {
        Color(
            red = (channel(0) / coverage).coerceIn(0f, 1f),
            green = (channel(1) / coverage).coerceIn(0f, 1f),
            blue = (channel(2) / coverage).coerceIn(0f, 1f),
            alpha = coverage.coerceIn(0f, 1f),
        )
    }
}

/** RGBA, premultiplied, per profile sample. */
private const val CHANNELS = 4
private const val ALPHA = 3

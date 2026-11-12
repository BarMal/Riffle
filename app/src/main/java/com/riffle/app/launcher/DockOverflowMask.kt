package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * The hint that a dock strip scrolls on past an edge: the strip's own content fades out toward
 * that edge, so whatever is behind the dock -- its glass, sheen and spotlight -- shows through.
 *
 * This replaces painting a dock-coloured gradient *over* the icons. That only matched a flat,
 * opaque fill; over the glass sheen, a translucent dock or the wallpaper, it read as a solid box
 * sliding across the dock. Fading the content's own alpha has no colour of its own to mismatch.
 *
 * The content is drawn into an offscreen layer (only while there is something to fade) and
 * masked with a baked one-pixel ramp, stretched across the run -- the same no-gradient-shader
 * approach as the dock's glass, see `GlassSheenStrength.kt`.
 */
internal fun Modifier.dockOverflowMask(
    runsHorizontally: Boolean,
    fadeStart: Boolean,
    fadeEnd: Boolean,
): Modifier {
    if (!fadeStart && !fadeEnd) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val runLength = if (runsHorizontally) size.width else size.height
            val alongPx = ceil(runLength).toInt()
            val mask =
                if (alongPx > 0) {
                    bakeOverflowMask(
                        alongPx = alongPx,
                        fadePx = DOCK_OVERFLOW_FADE_WIDTH_DP.dp.toPx(),
                        fadeStart = fadeStart,
                        fadeEnd = fadeEnd,
                        runsHorizontally = runsHorizontally,
                    )
                } else {
                    null
                }
            val crossPx = ceil(if (runsHorizontally) size.height else size.width).toInt()
            val dstSize = if (runsHorizontally) IntSize(alongPx, crossPx) else IntSize(crossPx, alongPx)
            onDrawWithContent {
                drawContent()
                if (mask != null) {
                    drawImage(
                        image = mask,
                        dstSize = dstSize,
                        blendMode = BlendMode.DstIn,
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
}

/**
 * The fade's alpha [positionPx] along a run of [runLengthPx]: 0 right at a fading edge, rising to
 * fully opaque across [fadePx], and opaque throughout an edge that does not fade.
 */
internal fun dockOverflowMaskAlpha(
    positionPx: Float,
    runLengthPx: Float,
    fadePx: Float,
    fadeStart: Boolean,
    fadeEnd: Boolean,
): Float {
    val fromStart = if (fadeStart && fadePx > 0f) (positionPx / fadePx).coerceIn(0f, 1f) else 1f
    val fromEnd = if (fadeEnd && fadePx > 0f) ((runLengthPx - positionPx) / fadePx).coerceIn(0f, 1f) else 1f
    return minOf(fromStart, fromEnd)
}

private fun bakeOverflowMask(
    alongPx: Int,
    fadePx: Float,
    fadeStart: Boolean,
    fadeEnd: Boolean,
    runsHorizontally: Boolean,
): ImageBitmap {
    val pixels =
        IntArray(alongPx) { index ->
            val alpha = dockOverflowMaskAlpha(index + 0.5f, alongPx.toFloat(), fadePx, fadeStart, fadeEnd)
            (alpha * MAX_CHANNEL + 0.5f).toInt() shl ALPHA_SHIFT
        }
    val width = if (runsHorizontally) alongPx else 1
    val height = if (runsHorizontally) 1 else alongPx
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val MAX_CHANNEL = 255f
private const val ALPHA_SHIFT = 24

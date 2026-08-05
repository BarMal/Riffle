package com.riffle.app.launcher.apps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.roundToInt

/**
 * Derives a single representative accent color from a decoded app icon bitmap, for use as a
 * per-app AdaptiveStage card tint (mirrors the reference "Calm" app, which tints cards from the
 * app icon's dominant hue instead of a deterministic per-package hash).
 *
 * Sampling approach: the bitmap is walked on a stride grid (every [strideX]/[strideY]-th pixel in
 * each dimension) rather than scanned fully, since launcher icons can be up to 320px per side and
 * this runs once per icon decode. Near-transparent pixels (alpha below [minAlpha]) are skipped, as
 * are near-gray pixels (saturation below [minSaturation]) since a gray pixel's hue is not
 * meaningful. Remaining pixels are bucketed by hue in [HUE_BUCKET_COUNT] buckets of
 * [HUE_BUCKET_DEGREES] degrees each; each pixel casts a vote into its bucket weighted by its own
 * saturation, so strongly saturated regions of the icon dominate the result even when a
 * desaturated region (e.g. a white/gray background fill) covers more pixels. The bucket with the
 * largest accumulated weight wins, and its saturation-weighted mean hue is used to build the
 * result color. Saturation and value are then pinned to FIXED constants matching
 * `adaptiveStageSeedColor`'s `0.46f`/`0.72f` (see `AdaptiveStageCardSurface.kt`) rather than using the
 * icon's own sampled saturation/value, so cards stay equally legible regardless of how dark,
 * light, or washed out the source icon happens to be.
 *
 * Returns null (never throws) when the bitmap is degenerate (zero-sized), fully/mostly
 * transparent, or has no sufficiently saturated pixels to form a meaningful hue — callers are
 * expected to fall back to the deterministic seed-hash color in that case.
 */
internal fun dominantColorOf(
    bitmap: ImageBitmap,
    strideX: Int = DEFAULT_SAMPLE_STRIDE,
    strideY: Int = DEFAULT_SAMPLE_STRIDE,
    minAlpha: Float = DEFAULT_MIN_ALPHA,
    minSaturation: Float = DEFAULT_MIN_SATURATION,
): Color? =
    runCatching {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return@runCatching null
        val safeStrideX = strideX.coerceAtLeast(1)
        val safeStrideY = strideY.coerceAtLeast(1)

        val pixelMap = bitmap.toPixelMap()
        val bucketWeights = DoubleArray(HUE_BUCKET_COUNT)
        val bucketHueWeightedSum = DoubleArray(HUE_BUCKET_COUNT)

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = pixelMap[x, y]
                if (pixel.alpha >= minAlpha) {
                    val hsv = pixel.toHsv()
                    val hue = hsv[0]
                    val saturation = hsv[1]
                    if (saturation >= minSaturation) {
                        val bucket =
                            (hue / HUE_BUCKET_DEGREES)
                                .toInt()
                                .coerceIn(0, HUE_BUCKET_COUNT - 1)
                        val weight = saturation.toDouble()
                        bucketWeights[bucket] += weight
                        bucketHueWeightedSum[bucket] += hue * weight
                    }
                }
                x += safeStrideX
            }
            y += safeStrideY
        }

        val bestBucket = bucketWeights.indices.maxByOrNull { index -> bucketWeights[index] }
        val bestWeight = bestBucket?.let { bucketWeights[it] } ?: 0.0
        if (bestBucket == null || bestWeight <= 0.0) {
            return@runCatching null
        }

        val meanHue = (bucketHueWeightedSum[bestBucket] / bestWeight).toFloat()
        Color.hsv(
            hue = meanHue.coerceIn(0f, MAX_HUE_DEGREES),
            saturation = FIXED_SATURATION,
            value = FIXED_VALUE,
        )
    }.getOrNull()

/** [hue in 0..360, saturation in 0..1, value in 0..1]. */
private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * COLOR_CHANNEL_MAX).roundToInt().coerceIn(0, COLOR_CHANNEL_MAX),
        (green * COLOR_CHANNEL_MAX).roundToInt().coerceIn(0, COLOR_CHANNEL_MAX),
        (blue * COLOR_CHANNEL_MAX).roundToInt().coerceIn(0, COLOR_CHANNEL_MAX),
        hsv,
    )
    return hsv
}

private const val DEFAULT_SAMPLE_STRIDE = 4
private const val DEFAULT_MIN_ALPHA = 0.5f
private const val DEFAULT_MIN_SATURATION = 0.15f
private const val HUE_BUCKET_COUNT = 36
private const val HUE_BUCKET_DEGREES = 10f
private const val MAX_HUE_DEGREES = 359.999f
private const val FIXED_SATURATION = 0.46f
private const val FIXED_VALUE = 0.72f
private const val COLOR_CHANNEL_MAX = 255

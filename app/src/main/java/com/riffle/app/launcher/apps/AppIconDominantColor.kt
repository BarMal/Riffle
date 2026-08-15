package com.riffle.app.launcher.apps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.palette.graphics.Palette

/**
 * Derives a single representative accent color from a decoded app icon bitmap, for use as a
 * per-app AdaptiveStage card tint (mirrors the reference "Calm" app, which tints cards from the
 * app icon's dominant hue instead of a deterministic per-package hash).
 *
 * Hue selection is delegated to [Palette], which quantizes the icon's colors and scores clusters
 * against perceptually-tuned vibrancy/lightness targets -- only the vibrant-family swatches
 * ([Palette.getVibrantSwatch], [Palette.getLightVibrantSwatch], [Palette.getDarkVibrantSwatch])
 * are considered, so a near-gray icon with no sufficiently saturated region yields no swatch,
 * matching the "fall back to the deterministic seed-hash color" contract callers rely on.
 * Saturation and value are then pinned to FIXED constants matching `adaptiveStageSeedColor`'s
 * `0.46f`/`0.72f` (see `AdaptiveStageCardSurface.kt`) rather than using the icon's own sampled
 * saturation/value, so cards stay equally legible regardless of how dark, light, or washed out
 * the source icon happens to be.
 *
 * Returns null (never throws) when the bitmap is degenerate, fully/mostly transparent, or has no
 * sufficiently vibrant region to form a meaningful hue.
 */
internal fun dominantColorOf(bitmap: ImageBitmap): Color? =
    runCatching {
        val palette = Palette.Builder(bitmap.asAndroidBitmap()).generate()
        val swatch =
            palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: return@runCatching null

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        Color.hsv(hue = hsv[0], saturation = FIXED_SATURATION, value = FIXED_VALUE)
    }.getOrNull()

private const val FIXED_SATURATION = 0.46f
private const val FIXED_VALUE = 0.72f

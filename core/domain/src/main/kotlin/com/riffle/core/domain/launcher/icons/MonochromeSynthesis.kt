package com.riffle.core.domain.launcher.icons

/**
 * "Theme all icons": the glyph layer generated for an app that ships no monochrome layer. The choice is the simplest
 * that reads well: desaturate (Rec. 709 luma) and threshold. The opaque pixels fall into a light class and a dark
 * class; the smaller class is the mark (a light mark on a dark field and a dark mark on a light field both come out as
 * the mark) and is softened over a small band so its edge is not jagged. An icon with one class only (a flat symbol)
 * keeps its whole silhouette. Pure, on ARGB pixels.
 */
object MonochromeSynthesis {
    private const val THRESHOLD = 0.5f
    private const val BAND = 0.12f
    private const val OPAQUE_ALPHA = 128
    private const val FULL = 255
    private const val MIN_MINORITY_SHARE = 0.05f

    /** The coverage (0..255) of the generated glyph for each of [argb]'s pixels; the glyph is painted in the theme. */
    fun coverage(argb: IntArray): IntArray {
        val lumas = FloatArray(argb.size) { luma(argb[it]) }
        var opaque = 0
        var light = 0
        for (i in argb.indices) {
            if (alpha(argb[i]) >= OPAQUE_ALPHA) {
                opaque++
                if (lumas[i] >= THRESHOLD) light++
            }
        }
        val dark = opaque - light
        val minority = minOf(light, dark)
        if (opaque == 0 || minority < opaque * MIN_MINORITY_SHARE) {
            // One class of pixels: the symbol is its silhouette.
            return IntArray(argb.size) { alpha(argb[it]) }
        }
        val markIsLight = light < dark
        return IntArray(argb.size) { i ->
            val strength = if (markIsLight) smooth(lumas[i] - THRESHOLD) else smooth(THRESHOLD - lumas[i])
            (alpha(argb[i]) * strength).toInt().coerceIn(0, FULL)
        }
    }

    // 0 below the band, 1 above it, linear in between: [delta] is how far the pixel is past the threshold.
    private fun smooth(delta: Float): Float = ((delta + BAND) / (2f * BAND)).coerceIn(0f, 1f)

    private fun alpha(argb: Int): Int = (argb ushr 24) and FULL

    private fun luma(argb: Int): Float {
        val r = (argb shr 16) and FULL
        val g = (argb shr 8) and FULL
        val b = argb and FULL
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / FULL
    }
}

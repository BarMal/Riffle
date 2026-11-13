package com.riffle.core.domain.launcher.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The glass fill and its legibility, as plain maths. The material is one flat translucent tint, so
 * what the screen shows is a plain source-over of the tint at its alpha over whatever is behind;
 * this is the formula the unit tests and the device tests derive their expectations from.
 */
object GlassBlend {
    /** The straight-alpha tint (r, g, b) at [alpha] over an opaque background: what the screen shows. */
    fun flatOver(
        tint: Triple<Float, Float, Float>,
        alpha: Float,
        background: Triple<Float, Float, Float>,
    ): Triple<Float, Float, Float> {
        val a = alpha.coerceIn(0f, 1f)
        return Triple(
            tint.first * a + background.first * (1 - a),
            tint.second * a + background.second * (1 - a),
            tint.third * a + background.third * (1 - a),
        )
    }

    /** WCAG relative luminance of an sRGB colour, channels 0..1. */
    fun luminance(
        r: Float,
        g: Float,
        b: Float,
    ): Float {
        fun lin(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }

    /** WCAG contrast ratio between two relative luminances. */
    fun contrast(
        l1: Float,
        l2: Float,
    ): Float = (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)

    /**
     * Text contrast of [text] on [surface] shown at [alpha] over the worst wallpaper for that text
     * (black for dark text, white for light text). Compositing is in sRGB, as the screen does it.
     */
    fun worstCaseTextContrast(
        surfaceRgb: Triple<Float, Float, Float>,
        textRgb: Triple<Float, Float, Float>,
        alpha: Float,
    ): Float {
        val textLuminance = luminance(textRgb.first, textRgb.second, textRgb.third)
        val wallpaper = if (textLuminance < 0.5f) 0f else 1f
        val shown =
            luminance(
                surfaceRgb.first * alpha + wallpaper * (1 - alpha),
                surfaceRgb.second * alpha + wallpaper * (1 - alpha),
                surfaceRgb.third * alpha + wallpaper * (1 - alpha),
            )
        return contrast(textLuminance, shown)
    }

    /** The minimum contrast ratio for body text (WCAG AA). */
    const val MINIMUM_TEXT_CONTRAST = 4.5f
}

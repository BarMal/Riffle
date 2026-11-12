package com.riffle.core.domain.launcher.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The glass shader's blend, as plain maths. The AGSL shader in `GlassSurface.kt` cannot run on the
 * JVM, so this is the formula it mirrors line for line, and it is what the unit tests pin down.
 *
 * The backdrop the shader samples is *premultiplied* RGBA, and it is transparent wherever the
 * system wallpaper shows (the wallpaper is outside the app's content). The glass therefore mixes
 * the backdrop towards the tint by an effective alpha, and keeps the backdrop's own alpha:
 * over opaque app content the result is opaque, exactly as a plain mix; over the transparent
 * wallpaper area it is the tint at the tint alpha, with the wallpaper showing through.
 */
object GlassBlend {
    /** A premultiplied colour: each of [r], [g], [b] is already multiplied by [a]. */
    data class Premultiplied(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
    ) {
        /** Source-over of this colour on an opaque straight-alpha background colour (what the screen shows). */
        fun overOpaque(
            backR: Float,
            backG: Float,
            backB: Float,
        ): Triple<Float, Float, Float> = Triple(r + backR * (1 - a), g + backG * (1 - a), b + backB * (1 - a))
    }

    /**
     * The tint alpha in force where the backdrop has [backdropAlpha]: [overContent] over fully
     * opaque app content, [overWallpaper] over a transparent backdrop, linear in between.
     */
    fun effectiveTintAlpha(
        backdropAlpha: Float,
        overContent: Float,
        overWallpaper: Float,
    ): Float {
        val opacity = backdropAlpha.coerceIn(0f, 1f)
        return (overWallpaper + (overContent - overWallpaper) * opacity).coerceIn(0f, 1f)
    }

    /**
     * Mixes the premultiplied [backdrop] towards the straight-alpha tint colour by [tintAlpha]:
     * `rgb = backdrop.rgb * (1 - t) + tint.rgb * t`, `a = backdrop.a * (1 - t) + t`.
     */
    fun tint(
        backdrop: Premultiplied,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        tintAlpha: Float,
    ): Premultiplied {
        val t = tintAlpha.coerceIn(0f, 1f)
        return Premultiplied(
            r = backdrop.r * (1 - t) + tintR * t,
            g = backdrop.g * (1 - t) + tintG * t,
            b = backdrop.b * (1 - t) + tintB * t,
            a = backdrop.a * (1 - t) + t,
        )
    }

    /**
     * Adds white light of strength [light] (the specular and rim terms). The light also raises the
     * alpha, so it stays visible over a transparent backdrop; over opaque content (a = 1) the
     * alpha is unchanged and the colour is the same additive light as before. The result is a
     * valid premultiplied colour (rgb never above alpha).
     */
    fun addLight(
        colour: Premultiplied,
        light: Float,
    ): Premultiplied {
        val a = min(1f, colour.a + light)
        return Premultiplied(min(colour.r + light, a), min(colour.g + light, a), min(colour.b + light, a), a)
    }

    /** The whole shader step after sampling: [effectiveTintAlpha], [tint] and [addLight]. */
    fun shade(
        backdrop: Premultiplied,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        tintAlphaOverContent: Float,
        tintAlphaOverWallpaper: Float,
        light: Float,
    ): Premultiplied {
        val t = effectiveTintAlpha(backdrop.a, tintAlphaOverContent, tintAlphaOverWallpaper)
        return addLight(tint(backdrop, tintR, tintG, tintB, t), light)
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

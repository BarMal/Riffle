package com.riffle.core.domain.launcher.designsystem

/**
 * The three weights of Riffle's one glass material (ADR 0003, "Material: one flat tint, lit at the
 * edge"). Every translucent surface is drawn by one primitive at one of these.
 *
 * - [Thin]: dock, chips, row backgrounds, page indicator. The lightest fill and edge lights.
 * - [Regular]: cards, widget frames, folders, the search pill.
 * - [Thick]: library sheet, menus, pickers, the focused card, settings sections. The densest fill.
 *
 * The weights differ in the alpha of their flat fill (and a little in how bright their edge lights
 * run); nothing else about the material changes between them.
 */
enum class GlassWeight {
    Thin,
    Regular,
    Thick,
}

/**
 * The numbers one [GlassWeight] owns. Feature code contains no literal surface alpha; it picks a
 * weight and these apply. All alphas are 0..1. Framework-free so the scale can be unit tested.
 */
data class GlassWeightTokens(
    /** The alpha of the flat, even fill over dark UI (the dark theme): a light blue-white tint. */
    val fillAlpha: Float,
    /** The alpha of the fill in the light theme, where the base is near-white and needs more body to be seen. */
    val lightFillAlpha: Float,
    /** The fill's base colour (0xRRGGBB) in the dark theme. */
    val darkBaseRgb: Int,
    /** The fill's base colour (0xRRGGBB) in the light theme. */
    val lightBaseRgb: Int,
    /** Scales the edge lights (border, specular hairline and tint strips) for this weight. */
    val edgeScale: Float,
)

object GlassMaterialTokens {
    // Signed off by the owner on a design canvas, over a dark wallpaper:
    //   Regular rgba(196, 202, 236, 0.16), Thin 0.12 of the same, Thick rgba(58, 62, 96, 0.56).
    private const val LIGHT_BLUE_WHITE = 0xC4CAEC
    private const val DEEP_INDIGO = 0x3A3E60
    private const val LIGHT_THEME_GLASS = 0xFAFAFF
    private const val LIGHT_THEME_THICK = 0xF2F3FA

    val thin =
        GlassWeightTokens(
            fillAlpha = 0.12f,
            lightFillAlpha = 0.30f,
            darkBaseRgb = LIGHT_BLUE_WHITE,
            lightBaseRgb = LIGHT_THEME_GLASS,
            edgeScale = 0.85f,
        )

    val regular =
        GlassWeightTokens(
            fillAlpha = 0.16f,
            lightFillAlpha = 0.40f,
            darkBaseRgb = LIGHT_BLUE_WHITE,
            lightBaseRgb = LIGHT_THEME_GLASS,
            edgeScale = 1f,
        )

    val thick =
        GlassWeightTokens(
            fillAlpha = 0.56f,
            lightFillAlpha = 0.78f,
            darkBaseRgb = DEEP_INDIGO,
            lightBaseRgb = LIGHT_THEME_THICK,
            edgeScale = 1.15f,
        )

    fun forWeight(weight: GlassWeight): GlassWeightTokens =
        when (weight) {
            GlassWeight.Thin -> thin
            GlassWeight.Regular -> regular
            GlassWeight.Thick -> thick
        }

    /** A glass fill: an 0xRRGGBB colour and the straight alpha it is drawn at. */
    data class Fill(
        val rgb: Int,
        val alpha: Float,
    )

    /**
     * The flat fill of a surface at [weight] whose caller's tint is [tintRgb]. [darkTheme] picks the
     * dark-theme base (light blue-white, or deep indigo for Thick) or the light-theme one. Under
     * [reducedTransparency] every weight takes the Thick fill at [REDUCED_TRANSPARENCY_FILL_ALPHA].
     */
    fun fill(
        weight: GlassWeight,
        tintRgb: Int,
        darkTheme: Boolean,
        reducedTransparency: Boolean = false,
    ): Fill {
        val tokens = forWeight(if (reducedTransparency) GlassWeight.Thick else weight)
        val base = if (darkTheme) tokens.darkBaseRgb else tokens.lightBaseRgb
        val alpha =
            when {
                reducedTransparency -> REDUCED_TRANSPARENCY_FILL_ALPHA
                darkTheme -> tokens.fillAlpha
                else -> tokens.lightFillAlpha
            }
        return Fill(mixRgb(tintRgb, base, BASE_SHARE), alpha)
    }

    /**
     * The Dock's fill alpha for its own "Dock background" setting of [percent] (0..100): clear at 0,
     * the Thin fill at the default [defaultPercent], opaque at 100, linear between. Returns the
     * alpha and how much of the dock's own surface colour replaces the glass base, 0 up to the
     * default and rising to 1 (so 100% is exactly the surface colour, as the setting promises).
     */
    fun dockFill(
        percent: Int,
        defaultPercent: Int,
        weightAlpha: Float,
    ): Pair<Float, Float> {
        val p = percent.coerceIn(0, 100)
        val d = defaultPercent.coerceIn(1, 99)
        return if (p <= d) {
            (weightAlpha * (p.toFloat() / d)) to 0f
        } else {
            val t = (p - d).toFloat() / (100 - d)
            (weightAlpha + (1f - weightAlpha) * t) to t
        }
    }

    /** Linear blend of two 0xRRGGBB colours: [toward] by [share] (0..1) from [from]. */
    fun mixRgb(
        from: Int,
        toward: Int,
        share: Float,
    ): Int {
        val s = if (share.isNaN()) 0f else share.coerceIn(0f, 1f)

        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (toward shr shift) and 0xFF
            return (a + (b - a) * s + 0.5f).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * How much of the fill colour is the weight's own base and how much the caller's tint (the theme's
     * surface, which carries dynamic colour): the base dominates so the glass reads as one material,
     * the tint keeps a trace of the wallpaper's hue.
     */
    const val BASE_SHARE = 0.8f

    /**
     * Reduced transparency: every surface takes the Thick fill (colour included) and a near-opaque
     * alpha, so nothing busy shows through behind text.
     */
    const val REDUCED_TRANSPARENCY_FILL_ALPHA = 0.92f

    /** Reduced transparency keeps the edge lights, but faint. */
    const val REDUCED_TRANSPARENCY_EDGE_SCALE = 0.5f

    /** Reduced transparency keeps the spotlight's colour cue at half strength. */
    const val REDUCED_TRANSPARENCY_SPOTLIGHT_SCALE = 0.5f
}

/**
 * The edge lights of the glass: everything the material draws besides its flat fill. Modelled on
 * the owner's desktop bar ("Serein", `GlassPill.qml` / `GlassPillOverlay.qml` / `Hairline.qml`):
 * a 1px border, a specular line along the top that fades out at both ends, and thin strips of tint
 * light along the top and bottom edges. The implied light is top-centre, so the top is brightest.
 *
 * Every thickness is a fixed dp: **nothing here scales with a surface's height**, which is what
 * produced the bevel of the previous sheen on tall surfaces. (The only height-dependent clamps are
 * for surfaces too short to hold a full-size strip.)
 *
 * The owner-signed values over a dark wallpaper: border white 14% (55% at the top edge, falling to a
 * faint 6% at the bottom), inner top hairline white 18% at its centre and 9% at 12% from either end,
 * gone at the ends; tint strips 7dp at 38% -> 0 along the top and 5dp at 0 -> 20% along the bottom.
 */
object GlassEdgeTokens {
    /** The border's thickness; it is drawn inside the shape. */
    const val BORDER_WIDTH_DP = 1f

    /** White alpha of the border at the very top edge, along the sides and at the very bottom edge. */
    const val BORDER_TOP_ALPHA = 0.55f
    const val BORDER_SIDE_ALPHA = 0.14f
    const val BORDER_BOTTOM_ALPHA = 0.06f

    /** The border falls from its top alpha to its side alpha over this many dp from the top edge... */
    const val BORDER_TOP_FALLOFF_DP = 24f

    /** ...and rises from its side alpha to its bottom alpha over this many dp above the bottom edge. */
    const val BORDER_BOTTOM_RISE_DP = 16f

    /** The specular hairline: 1dp, just inside the border, brightest at the middle, gone at both ends. */
    const val SPECULAR_THICKNESS_DP = 1f
    const val SPECULAR_PEAK_ALPHA = 0.18f
    const val SPECULAR_SHOULDER_ALPHA = 0.09f

    /** Where along its length (from either end) the hairline reaches its shoulder alpha. */
    const val SPECULAR_SHOULDER_STOP = 0.12f

    /** Tint light along the top edge: this tall, [TINT_TOP_ALPHA] -> 0 downwards. */
    const val TINT_TOP_STRIP_DP = 7f
    const val TINT_TOP_ALPHA = 0.38f

    /** Tint light along the bottom edge: this tall, 0 -> [TINT_BOTTOM_ALPHA] downwards. */
    const val TINT_BOTTOM_STRIP_DP = 5f
    const val TINT_BOTTOM_ALPHA = 0.20f

    /** A strip never takes more than this share of a short surface (Serein's `height * 0.22` clamp). */
    const val TINT_STRIP_MAX_FRACTION = 0.22f

    /** Light glass swallows white light: edge alphas are boosted by up to this much as it gets lighter. */
    const val LIGHT_SURFACE_BOOST = 1.6f

    /** Seen luminance at and below which glass reads as dark (boost 1) and at and above which fully light. */
    const val DARK_LUMINANCE = 0.3f
    const val LIGHT_LUMINANCE = 0.7f

    /** Riffle's default wallpapers and most photos sit around here; used for the see-through share. */
    const val ASSUMED_WALLPAPER_LUMINANCE = 0.3f

    /**
     * How light a fill of [surfaceLuminance] at [fillAlpha] reads: its own luminance weighted by its
     * opacity, over an assumed mid-dark wallpaper for the rest.
     */
    fun seenLuminance(
        surfaceLuminance: Float,
        fillAlpha: Float,
    ): Float {
        val opacity = fillAlpha.coerceIn(0f, 1f)
        return surfaceLuminance * opacity + ASSUMED_WALLPAPER_LUMINANCE * (1f - opacity)
    }

    /** How light glass whose seen luminance is [seenLuminance] reads: 0 for dark glass, 1 for fully light. */
    fun lightness(seenLuminance: Float): Float =
        ((seenLuminance - DARK_LUMINANCE) / (LIGHT_LUMINANCE - DARK_LUMINANCE)).coerceIn(0f, 1f)

    /** The multiplier on edge alphas for glass whose seen luminance is [seenLuminance] (0..1). */
    fun lightSurfaceBoost(seenLuminance: Float): Float = 1f + (LIGHT_SURFACE_BOOST - 1f) * lightness(seenLuminance)

    /**
     * How far the border's sides and bottom turn from white toward black as the glass gets lighter
     * (the top edge, the lit one, stays white): white light on a light pane is invisible, but its
     * shaded edge is not. Serein's light mode does the same with a black 12% border.
     */
    const val LIGHT_SURFACE_SHADE_MIX = 0.85f

    /** One colour stop of a vertical ramp: [fraction] of the surface's height, white [alpha]. */
    data class Stop(
        val fraction: Float,
        val alpha: Float,
    )

    /**
     * The border's vertical alpha ramp for a surface [heightDp] tall, [scale] applied to every
     * alpha. The stops sit at fixed dp distances from the top and bottom edges, so a 400dp card has
     * the same bright 24dp at the top as a 48dp pill; only a surface shorter than the two ramps
     * together shares them proportionally. Fractions are strictly increasing from 0 to 1.
     */
    fun borderStops(
        heightDp: Float,
        scale: Float = 1f,
    ): List<Stop> {
        val height = heightDp.coerceAtLeast(1f)
        val ramps = BORDER_TOP_FALLOFF_DP + BORDER_BOTTOM_RISE_DP
        val shrink = if (height < ramps) height / ramps else 1f
        val fallEnd = BORDER_TOP_FALLOFF_DP * shrink / height
        val riseStart = 1f - BORDER_BOTTOM_RISE_DP * shrink / height

        fun alpha(base: Float) = (base * scale).coerceIn(0f, 1f)
        return buildList {
            add(Stop(0f, alpha(BORDER_TOP_ALPHA)))
            add(Stop(fallEnd, alpha(BORDER_SIDE_ALPHA)))
            if (riseStart > fallEnd) add(Stop(riseStart, alpha(BORDER_SIDE_ALPHA)))
            add(Stop(1f, alpha(BORDER_BOTTOM_ALPHA)))
        }
    }

    /** The height in dp of a tint strip of [stripDp] on a surface [heightDp] tall. */
    fun tintStripHeightDp(
        stripDp: Float,
        heightDp: Float,
    ): Float = minOf(stripDp, heightDp * TINT_STRIP_MAX_FRACTION)
}

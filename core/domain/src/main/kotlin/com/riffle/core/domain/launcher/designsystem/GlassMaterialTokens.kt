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
    /** The alpha of the flat, even fill over whatever is behind the surface. */
    val fillAlpha: Float,
    /**
     * The least fill alpha at which the surface's text keeps 4.5:1 over the worst wallpaper (see
     * `GlassBlend`). The wallpaper is outside the app, so nothing here can sample or blur it:
     * [fillAlpha] must never fall below this.
     */
    val legibilityFloorAlpha: Float,
    /** Scales the edge lights (border, specular hairline and tint strips) for this weight. */
    val edgeScale: Float,
)

object GlassMaterialTokens {
    val thin =
        GlassWeightTokens(
            fillAlpha = 0.66f,
            legibilityFloorAlpha = 0.66f,
            edgeScale = 0.85f,
        )

    val regular =
        GlassWeightTokens(
            fillAlpha = 0.72f,
            legibilityFloorAlpha = 0.66f,
            edgeScale = 1f,
        )

    val thick =
        GlassWeightTokens(
            fillAlpha = 0.80f,
            legibilityFloorAlpha = 0.72f,
            edgeScale = 1.15f,
        )

    fun forWeight(weight: GlassWeight): GlassWeightTokens =
        when (weight) {
            GlassWeight.Thin -> thin
            GlassWeight.Regular -> regular
            GlassWeight.Thick -> thick
        }

    /** Reduced transparency: the fill is fully opaque and nothing behind the surface shows. */
    const val REDUCED_TRANSPARENCY_FILL_ALPHA = 1f

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
 * Serein's own values, for reference: border white 10% (chromatic blue 7..18%); specular rim line
 * 26% at the centre, 10% at 12% from either end, 0 at the ends; tint strips 7px at 38% -> 0 along
 * the top and 5px at 0 -> 20% along the bottom. The border here runs a little brighter than
 * Serein's 10% (and its specular line brighter than Serein's 26%), because the owner asked for more
 * emphasis on the edge lights, and fades down the sides like the light it is.
 */
object GlassEdgeTokens {
    /** The border's thickness; it is drawn inside the shape. */
    const val BORDER_WIDTH_DP = 1f

    /** White alpha of the border at the very top edge, along the sides and at the very bottom edge. */
    const val BORDER_TOP_ALPHA = 0.55f
    const val BORDER_SIDE_ALPHA = 0.20f
    const val BORDER_BOTTOM_ALPHA = 0.09f

    /** The border falls from its top alpha to its side alpha over this many dp from the top edge... */
    const val BORDER_TOP_FALLOFF_DP = 24f

    /** ...and rises from its side alpha to its bottom alpha over this many dp above the bottom edge. */
    const val BORDER_BOTTOM_RISE_DP = 16f

    /** The specular hairline: 1dp, just inside the border, brightest at the middle, gone at both ends. */
    const val SPECULAR_THICKNESS_DP = 1f
    const val SPECULAR_PEAK_ALPHA = 0.42f
    const val SPECULAR_SHOULDER_ALPHA = 0.16f

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

package com.riffle.core.domain.launcher.designsystem

/**
 * The fill of a Stream card (ADR 0003, Amendment 1, "Card colour"): the glass tinted with the card's app's main icon
 * colour, in a plain solid colour (no gradient, no blur), and the colour of the text that sits on it. The edge stays
 * the neutral glass edge; the app colour is in the body only.
 *
 * The tint is [strengthPercent] of the app colour mixed over the fill colour of a card. The weight's own alpha would
 * keep a card very see-through, and dense text cannot be read through a 16% fill over a bright or dark wallpaper (the
 * lightest weights are why dense text sits on Thick, see [GlassBlend]), so a card takes Thick's colours (the dark
 * theme's a little deeper, [DARK_CARD_BASE_RGB]) and the lowest alpha, no lower than Regular's, at which its text
 * keeps [GlassBlend.MINIMUM_TEXT_CONTRAST] over the worst of a black, a mid-grey and a white backdrop ([BACKDROPS]).
 * The text colour is whichever of white and black does better on the result, so a pale app colour on a dark theme
 * turns its text dark rather than losing it. Every number is pure, so the unit tests pin the contrast over a spread
 * of colours, strengths and both themes.
 */
object CardTint {
    const val DEFAULT_STRENGTH_PERCENT = 22
    const val MIN_STRENGTH_PERCENT = 0
    const val MAX_STRENGTH_PERCENT = 40
    const val STRENGTH_STEP_PERCENT = 2

    /** The backdrops a card's text must stay readable over: black, a mid-grey and white (0..1 per channel). */
    val BACKDROPS: List<Float> = listOf(0f, 0.5f, 1f)

    /**
     * A card's fill colour in the dark theme: Thick's deep indigo, darker still, so that light text keeps its contrast
     * over a white wallpaper at an alpha that still lets the wallpaper through (Thick's own would need nearly opaque).
     * The light theme takes Thick's pale grey-white as it is.
     */
    const val DARK_CARD_BASE_RGB = 0x181A2E

    private const val WHITE_INK = 0xFFFFFF
    private const val BLACK_INK = 0x000000

    /** The alpha is searched upward in steps of this much; the last step is the ceiling. */
    private const val ALPHA_STEP = 0.01f
    private const val MAX_ALPHA = 0.97f

    /** A little over the minimum, so rounding in the screen's compositing never lands just under it. */
    private const val CONTRAST_MARGIN = 0.05f

    /** How far the fill is pushed toward black or white, a step at a time, when no alpha alone is enough. */
    private const val PUSH_STEP = 0.05f

    /** A card's fill: the colour (0xRRGGBB), its straight alpha and the colour of the text on it (0xRRGGBB). */
    data class CardFill(
        val rgb: Int,
        val alpha: Float,
        val inkRgb: Int,
    )

    fun coercedStrength(percent: Int): Int = percent.coerceIn(MIN_STRENGTH_PERCENT, MAX_STRENGTH_PERCENT)

    /**
     * The fill of a card of [appRgb] (null: the app has no vivid colour, or there is no app: no tint) at
     * [strengthPercent], in the dark or the light theme. Under [reducedTransparency] the fill is the near-opaque one
     * every glass surface takes, tinted the same way.
     */
    fun fill(
        appRgb: Int?,
        strengthPercent: Int,
        darkTheme: Boolean,
        reducedTransparency: Boolean = false,
    ): CardFill {
        val base = if (darkTheme) DARK_CARD_BASE_RGB else GlassMaterialTokens.thick.lightBaseRgb
        val share = coercedStrength(strengthPercent) / PERCENT
        val tinted = if (appRgb == null) base else GlassMaterialTokens.mixRgb(base, appRgb and RGB_MASK, share)
        return if (reducedTransparency) {
            val alpha = GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA
            CardFill(tinted, alpha, bestInk(tinted, alpha))
        } else {
            readableFill(tinted)
        }
    }

    /** The first fill, going up in alpha from Regular's, that reads; else the colour pushed toward its text's end. */
    private fun readableFill(tinted: Int): CardFill {
        val from = GlassMaterialTokens.regular.fillAlpha
        val alphas = generateSequence(from) { it + ALPHA_STEP }.takeWhile { it <= MAX_ALPHA }
        return alphas.map { alpha -> CardFill(tinted, alpha, bestInk(tinted, alpha)) }.firstOrNull(::reads)
            ?: pushedFill(tinted)
    }

    /**
     * No alpha alone does it (a mid-lightness colour, where white and black text both fall just short): push the fill
     * toward whichever end its text is not at, a step at a time, until it reads (and at the last step regardless).
     */
    private fun pushedFill(tinted: Int): CardFill {
        val toward = if (bestInk(tinted, MAX_ALPHA) == WHITE_INK) BLACK_INK else WHITE_INK
        val steps = generateSequence(PUSH_STEP) { it + PUSH_STEP }.takeWhile { it <= 1f }
        val fills =
            steps.map { push ->
                val pushed = GlassMaterialTokens.mixRgb(tinted, toward, push)
                CardFill(pushed, MAX_ALPHA, bestInk(pushed, MAX_ALPHA))
            }
        return fills.firstOrNull(::reads) ?: fills.last()
    }

    private fun reads(fill: CardFill): Boolean =
        worstContrast(fill.rgb, fill.alpha, fill.inkRgb) >= GlassBlend.MINIMUM_TEXT_CONTRAST + CONTRAST_MARGIN

    /** Whichever of white and black text has the better worst-case contrast on [rgb] at [alpha]. */
    fun bestInk(
        rgb: Int,
        alpha: Float,
    ): Int = if (worstContrast(rgb, alpha, WHITE_INK) >= worstContrast(rgb, alpha, BLACK_INK)) WHITE_INK else BLACK_INK

    /** The lowest contrast of [inkRgb] on the fill [rgb] at [alpha] over any of the [BACKDROPS]. */
    fun worstContrast(
        rgb: Int,
        alpha: Float,
        inkRgb: Int,
    ): Float {
        val inkLuminance = luminanceOf(inkRgb)
        return BACKDROPS.minOf { backdrop ->
            val shown = GlassBlend.flatOver(channels(rgb), alpha, Triple(backdrop, backdrop, backdrop))
            GlassBlend.contrast(inkLuminance, GlassBlend.luminance(shown.first, shown.second, shown.third))
        }
    }

    private fun luminanceOf(rgb: Int): Float {
        val (r, g, b) = channels(rgb)
        return GlassBlend.luminance(r, g, b)
    }

    private fun channels(rgb: Int): Triple<Float, Float, Float> =
        Triple(
            (rgb shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
            (rgb shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
            (rgb and CHANNEL_MASK) / CHANNEL_MAX,
        )

    private const val PERCENT = 100f
    private const val RGB_MASK = 0xFFFFFF
    private const val CHANNEL_MASK = 0xFF
    private const val CHANNEL_MAX = 255f
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}

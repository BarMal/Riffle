package com.riffle.core.domain.launcher.designsystem

/**
 * The three weights of Riffle's one glass material (ADR 0003, "Material: one glass, with the
 * spotlight everywhere"). Every translucent surface is drawn by one primitive at one of these.
 *
 * - [Thin]: dock, chips, row backgrounds, page indicator. Light frost, hairline, no shadow.
 * - [Regular]: cards, widget frames, folders, the search pill. Full frost, hairline, one soft shadow.
 * - [Thick]: library sheet, menus, pickers, the focused card, settings sections. Heavy frost with a
 *   contrast scrim behind text; no shadow of its own, since these sit on a page or a sheet.
 */
enum class GlassWeight {
    Thin,
    Regular,
    Thick,
}

/**
 * The numbers one [GlassWeight] owns: frost, tint alpha, scrim, hairline and shadow. Feature code
 * contains no literal surface alpha or blur radius; it picks a weight and these apply.
 *
 * All alphas are 0..1, all sizes are dp. Framework-free so the scale can be unit tested.
 */
data class GlassWeightTokens(
    /** Multiplies the user's frost strength (0..1) before it reaches the shader. */
    val frostScale: Float,
    /** Tint alpha when the tint layer is blurred in software (no backdrop to sample). */
    val tintAlpha: Float,
    /** Tint alpha below the API level where blur exists at all: a stronger flat scrim instead. */
    val noBlurTintAlpha: Float,
    /** Blur radius of the software tint layer. */
    val blurRadiusDp: Int,
    /** The shader tint's alpha at zero frost... */
    val shaderTintBaseAlpha: Float,
    /** ...plus this much more at full frost. */
    val shaderTintFrostAlpha: Float,
    /** Extra flat contrast layer over the glass and under the content; 0 for none. */
    val contentScrimAlpha: Float,
    /** Scales the sheen's border (the hairline). */
    val hairlineScale: Float,
    /** Scales the sheen's specular, caustic and rim highlights. */
    val highlightScale: Float,
    /** Scales the sheen's bottom inner shadow. */
    val innerShadowScale: Float,
    /** The one soft drop shadow, in dp; 0 for none. */
    val shadowElevationDp: Int,
) {
    /** The frost the shader receives for the user's [userFrost] (0..1). */
    fun effectiveFrost(userFrost: Float): Float = (userFrost * frostScale).coerceIn(0f, 1f)

    /** The shader tint's alpha at [frost] (already effective, 0..1). */
    fun shaderTintAlpha(frost: Float): Float =
        (shaderTintBaseAlpha + shaderTintFrostAlpha * frost.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** The tint alpha of the software tint layer. */
    fun softwareTintAlpha(supportsBlur: Boolean): Float = if (supportsBlur) tintAlpha else noBlurTintAlpha
}

object GlassMaterialTokens {
    val thin =
        GlassWeightTokens(
            frostScale = 0.7f,
            tintAlpha = 0.40f,
            noBlurTintAlpha = 0.65f,
            blurRadiusDp = 16,
            shaderTintBaseAlpha = 0.25f,
            shaderTintFrostAlpha = 0.35f,
            contentScrimAlpha = 0f,
            hairlineScale = 0.6f,
            highlightScale = 0.8f,
            innerShadowScale = 0.8f,
            shadowElevationDp = 0,
        )

    val regular =
        GlassWeightTokens(
            frostScale = 1f,
            tintAlpha = 0.55f,
            noBlurTintAlpha = 0.75f,
            blurRadiusDp = 24,
            shaderTintBaseAlpha = 0.35f,
            shaderTintFrostAlpha = 0.45f,
            contentScrimAlpha = 0f,
            hairlineScale = 1f,
            highlightScale = 1f,
            innerShadowScale = 1f,
            shadowElevationDp = RiffleElevationScale.LEVEL_2,
        )

    val thick =
        GlassWeightTokens(
            frostScale = 1.25f,
            tintAlpha = 0.72f,
            noBlurTintAlpha = 0.88f,
            blurRadiusDp = 32,
            shaderTintBaseAlpha = 0.50f,
            shaderTintFrostAlpha = 0.45f,
            contentScrimAlpha = 0.14f,
            hairlineScale = 1.3f,
            highlightScale = 1.1f,
            innerShadowScale = 1.2f,
            shadowElevationDp = 0,
        )

    fun forWeight(weight: GlassWeight): GlassWeightTokens =
        when (weight) {
            GlassWeight.Thin -> thin
            GlassWeight.Regular -> regular
            GlassWeight.Thick -> thick
        }

    /** Reduced transparency: the tint is fully opaque and nothing behind the surface shows. */
    const val REDUCED_TRANSPARENCY_TINT_ALPHA = 1f

    /** Reduced transparency keeps the sheen, but faint. */
    const val REDUCED_TRANSPARENCY_SHEEN_SCALE = 0.35f

    /** Reduced transparency keeps the spotlight's colour cue at half strength. */
    const val REDUCED_TRANSPARENCY_SPOTLIGHT_SCALE = 0.5f
}

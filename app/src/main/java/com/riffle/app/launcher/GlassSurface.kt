package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight

/**
 * Riffle's one glass material (ADR 0003): a flat, even translucent tint with its light at the
 * edges, drawn at one of three [GlassWeight]s. It is the only way a surface gets a translucent
 * background: the weight owns the fill alpha and edge strength (see `GlassMaterialTokens`), so call
 * sites carry no literal surface alpha.
 *
 * It is modelled on the owner's desktop bar (the "Serein" pill): the **fill** is one flat tint, no
 * gradient across the body, no bevel, no inner shadow, no refraction; the **edge lights** (see
 * `GlassEdgeLights.kt`) are a 1dp border that is brightest along the top and fades down the sides,
 * following [shape] exactly, plus a soft specular hairline just inside the top edge. Nothing
 * scales with the surface's height, so a pill and a tall card carry the same edge.
 *
 * The system wallpaper is outside the app, so glass cannot blur it (`design-language.md` section
 * 3); the fill is a light, see-through tint (Thin 12 percent, Regular 16, Thick a darker 56) over
 * the wallpaper, and the material needs no backdrop capture.
 *
 * Glass is always on. The one fallback that is part of the material is reduced transparency
 * ([ResolvedLiquidGlass.reducedTransparency]): the Thick fill, near opaque, the edge lights kept but
 * faint, the spotlight at half strength.
 *
 * **Tint light**: a surface with a colour shows it as light, never as paint. With [spotlight] true
 * the surface lights its long edges with the colours of the things it holds -- content reports each
 * through [glassSpotlightSource] -- and it asks for frames only while those colours move (see
 * `GlassSpotlight.kt`). A surface with one fixed colour passes it as [edgeTint] and gets thin strips
 * of it along its top and bottom edges. The fill itself stays [tint], a neutral surface colour.
 *
 * [tint] supplies a trace of the fill colour (and says whether the theme is dark); the base colour
 * and the alpha are the weight's to set (see [glassFillColor]). [content] is drawn on top,
 * clipped to the same [shape], with [contentColor] (default: the colour that contrasts [tint]).
 */
@Composable
@Suppress("LongParameterList")
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    weight: GlassWeight = GlassWeight.Regular,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = Color.Unspecified,
    spotlight: Boolean = false,
    /** With [spotlight]: each thing lights only the rim near it (a card), the rest of the edge staying neutral. */
    spotlightLocal: Boolean = false,
    edgeTint: Color = Color.Unspecified,
    cardTint: GlassCardTint? = null,
    content: @Composable () -> Unit = {},
) {
    val reducedTransparency = LocalLiquidGlassSettings.current.reducedTransparency
    val card = cardTint?.let { spec -> resolveCardFill(spec, tint, reducedTransparency) }
    val fill = card?.fill ?: glassFillColor(weight, tint, reducedTransparency)
    val spotlightState =
        rememberGlassSpotlightState(
            enabled = spotlight,
            localReachDp = CARD_SPOTLIGHT_REACH_DP.takeIf { spotlightLocal },
        )
    val resolvedContentColor = contentColor.takeOrElse { card?.ink ?: contentColorFor(tint) }
    // A card's coloured glow, when the setting asks for one, is the edge's tint light along the top edge only.
    val glow = if (cardTint?.topGlow == true && cardTint.appColor != null) cardTint.appColor else Color.Unspecified

    Box(modifier = modifier.clip(shape)) {
        Box(Modifier.matchParentSize().background(fill))
        if (spotlightState != null) {
            Box(Modifier.matchParentSize().glassSpotlight(spotlightState, shape))
        }
        Box(
            Modifier.matchParentSize().glassEdgeLights(
                shape,
                weight,
                fill,
                edgeTint.takeOrElse { glow },
                tintBottom = glow == Color.Unspecified || edgeTint != Color.Unspecified,
            ),
        )
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
            LocalGlassSpotlight provides spotlightState,
        ) {
            if (card != null && contentColor == Color.Unspecified) {
                // The card's text colour follows its fill: the theme's own text colours would not be readable on it.
                CardTextColors(card.ink, content)
            } else {
                content()
            }
        }
    }
}

/**
 * A Stream card's tint (ADR 0003, Amendment 1, "Card colour"): its app's main icon colour (null: no tint) and how much
 * of it the fill takes, in percent, and whether the colour also glows along the top edge. The fill is a plain solid
 * colour from `CardTint`, never a gradient; the edge stays the neutral glass edge.
 */
internal class GlassCardTint(
    val appColor: Color?,
    val strengthPercent: Int,
    val topGlow: Boolean = false,
)

private class ResolvedCardFill(
    val fill: Color,
    val ink: Color,
)

private fun resolveCardFill(
    spec: GlassCardTint,
    tint: Color,
    reducedTransparency: Boolean,
): ResolvedCardFill {
    val fill =
        CardTint.fill(
            appRgb = spec.appColor?.toArgb()?.and(RGB_MASK),
            strengthPercent = spec.strengthPercent,
            darkTheme = tint.luminance() < DARK_TINT_LUMINANCE,
            reducedTransparency = reducedTransparency,
        )
    return ResolvedCardFill(
        fill = Color(OPAQUE_BLACK or fill.rgb).copy(alpha = fill.alpha),
        ink = Color(OPAQUE_BLACK or fill.inkRgb),
    )
}

/** [content] with the theme's text colours on surfaces swapped for [ink] (and a softer [ink] for secondary text). */
@Composable
private fun CardTextColors(
    ink: Color,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme =
            MaterialTheme.colorScheme.copy(
                onSurface = ink,
                onSurfaceVariant = ink.copy(alpha = CARD_SECONDARY_TEXT_ALPHA),
                // Accent text (a time span, a text button) is the ink too: the theme's accent may not be readable.
                primary = ink,
            ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Secondary text on a card (the app name, the time): the ink, softened. */
private const val CARD_SECONDARY_TEXT_ALPHA = 0.8f

/**
 * The flat fill [GlassSurface] draws at [weight] for a caller whose tint is [tint]: the weight's own
 * base colour (light blue-white in the dark theme, near-white in the light one, deep indigo or pale
 * grey for Thick) with a share of [tint] mixed in so dynamic colour still reads, at the weight's
 * alpha. Whether the theme is dark is read from [tint]'s luminance. Under reduced transparency
 * every weight takes the Thick fill, near opaque.
 */
internal fun glassFillColor(
    weight: GlassWeight,
    tint: Color,
    reducedTransparency: Boolean = false,
): Color {
    val fill =
        GlassMaterialTokens.fill(
            weight = weight,
            tintRgb = tint.toArgb() and RGB_MASK,
            darkTheme = tint.luminance() < DARK_TINT_LUMINANCE,
            reducedTransparency = reducedTransparency,
        )
    return Color(OPAQUE_BLACK or fill.rgb).copy(alpha = fill.alpha)
}

private const val RGB_MASK = 0xFFFFFF
private const val OPAQUE_BLACK = 0xFF000000.toInt()
private const val DARK_TINT_LUMINANCE = 0.5f

/**
 * Source compatibility only: Home's root once installed a backdrop capture here for the refraction
 * shader to sample. The flat material samples nothing, so this just runs [content] with a no-op
 * capture modifier. Its one caller (`LauncherHomeDestination`) can drop the wrapper.
 */
@Composable
internal fun ProvideLiquidGlassBackdrop(content: @Composable (backdropCapture: Modifier) -> Unit) {
    content(Modifier)
}

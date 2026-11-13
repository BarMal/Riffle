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
import androidx.compose.ui.graphics.takeOrElse
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
 * 3); the fill alpha is therefore chosen to keep text legible over the worst wallpaper (the
 * weight's legibility floor) and the material needs no backdrop capture.
 *
 * Glass is always on. The one fallback that is part of the material is reduced transparency
 * ([ResolvedLiquidGlass.reducedTransparency]): an opaque fill, the edge lights kept but faint, the
 * spotlight at half strength.
 *
 * **Tint light**: a surface with a colour shows it as light, never as paint. With [spotlight] true
 * the surface lights its long edges with the colours of the things it holds -- content reports each
 * through [glassSpotlightSource] -- and it asks for frames only while those colours move (see
 * `GlassSpotlight.kt`). A surface with one fixed colour passes it as [edgeTint] and gets thin strips
 * of it along its top and bottom edges. The fill itself stays [tint], a neutral surface colour.
 *
 * [tint] supplies the fill colour only; its alpha is the weight's to set. [content] is drawn on top,
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
    edgeTint: Color = Color.Unspecified,
    content: @Composable () -> Unit = {},
) {
    val fillAlpha =
        if (LocalLiquidGlassSettings.current.reducedTransparency) {
            GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA
        } else {
            GlassMaterialTokens.forWeight(weight).fillAlpha
        }
    val fill = tint.copy(alpha = fillAlpha)
    val spotlightState = rememberGlassSpotlightState(enabled = spotlight)
    val resolvedContentColor = contentColor.takeOrElse { contentColorFor(tint) }

    Box(modifier = modifier.clip(shape)) {
        Box(Modifier.matchParentSize().background(fill))
        if (spotlightState != null) {
            Box(Modifier.matchParentSize().glassSpotlight(spotlightState, shape))
        }
        Box(Modifier.matchParentSize().glassEdgeLights(shape, weight, fill, edgeTint))
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
            LocalGlassSpotlight provides spotlightState,
        ) {
            content()
        }
    }
}

/**
 * Source compatibility only: Home's root once installed a backdrop capture here for the refraction
 * shader to sample. The flat material samples nothing, so this just runs [content] with a no-op
 * capture modifier. Its one caller (`LauncherHomeDestination`) can drop the wrapper.
 */
@Composable
internal fun ProvideLiquidGlassBackdrop(content: @Composable (backdropCapture: Modifier) -> Unit) {
    content(Modifier)
}

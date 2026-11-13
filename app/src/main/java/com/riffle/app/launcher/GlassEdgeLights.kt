package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.GlassEdgeTokens
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight

/*
 * The edge lights: everything the glass draws besides its flat fill. Modelled on the desktop bar's
 * pill (`GlassPill.qml` / `GlassPillOverlay.qml` / `Hairline.qml`), lit from the top centre:
 *
 *  1. Tint light -- when the surface has a colour, a thin strip of it along the top edge (7dp,
 *                   38% -> 0) and a fainter one along the bottom (5dp, 0 -> 20%).
 *  2. Specular   -- a 1dp white hairline just inside the top edge, brightest in the middle and
 *                   gone at both ends.
 *  3. Border     -- a 1dp white outline that follows the shape exactly, brightest along the top
 *                   and fading down the sides to a faint bottom edge.
 *
 * Every thickness is a fixed dp (`GlassEdgeTokens`): none of it scales with the surface's height,
 * so a 48dp pill and a 400dp card carry the same edge. It is plain Compose drawing -- a gradient
 * stroke along the shape's own outline -- so it renders identically in the JVM screenshot tests.
 * Nothing animates here; the spotlight's own frames are the only motion on a surface.
 */

/**
 * Draws the edge lights over this node's fill and under its content, following [shape]. The node
 * must sit inside the surface's own `clip(shape)`: the border is stroked centred on the edge and
 * the clip removes the outer half, which leaves 1dp inside. [fill] is the surface's flat fill and
 * says how light the glass reads (light glass swallows white light, so its edge lights are
 * boosted); [edgeTint], if specified, is the colour whose light runs along the top and bottom
 * edges, its alpha the intensity. [alphaProvider] is the surface's own fade (the dock pull's), read
 * at draw time. Under reduced transparency the edge lights are kept, but faint.
 */
@Composable
internal fun Modifier.glassEdgeLights(
    shape: Shape,
    weight: GlassWeight,
    fill: Color,
    edgeTint: Color = Color.Unspecified,
    alphaProvider: () -> Float = { 1f },
): Modifier {
    val reduced = LocalLiquidGlassSettings.current.reducedTransparency
    val tokens = GlassMaterialTokens.forWeight(weight)
    val seen = GlassEdgeTokens.seenLuminance(fill.luminance(), fill.alpha)
    val boost = GlassEdgeTokens.lightSurfaceBoost(seen)
    // The lit top edge is white; on light glass the shaded sides and bottom turn dark so they still read.
    val shadeMix = GlassEdgeTokens.lightness(seen) * GlassEdgeTokens.LIGHT_SURFACE_SHADE_MIX
    val shade = lerp(Color.White, Color.Black, shadeMix)
    val reducedScale = if (reduced) GlassMaterialTokens.REDUCED_TRANSPARENCY_EDGE_SCALE else 1f
    val scale = tokens.edgeScale * boost * reducedScale
    return drawWithCache {
        val lights = if (size.width >= 1f && size.height >= 1f) bakeEdgeLights(shape, scale, shade, edgeTint) else null
        onDrawWithContent {
            val fade = alphaProvider().coerceIn(0f, 1f)
            if (lights != null && fade > 0f) lights.draw(this, fade)
            drawContent()
        }
    }
}

/** A rectangle filled with a brush: one strip of light. */
private class Strip(
    val brush: Brush,
    val topLeft: Offset,
    val size: Size,
)

/** The edge lights as brushes over fixed rectangles and one outline, built once per size. */
private class EdgeLights(
    val tintStrips: List<Strip>,
    val specular: Strip,
    val border: Outline,
    val borderBrush: Brush,
    val borderStroke: Stroke,
) {
    fun draw(
        scope: DrawScope,
        fade: Float,
    ) = with(scope) {
        (tintStrips + specular).forEach { strip -> drawRect(strip.brush, strip.topLeft, strip.size, alpha = fade) }
        drawOutline(border, borderBrush, alpha = fade, style = borderStroke)
    }
}

private fun CacheDrawScope.bakeEdgeLights(
    shape: Shape,
    scale: Float,
    shade: Color,
    edgeTint: Color,
): EdgeLights {
    val heightDp = size.height / density
    val white = { alpha: Float -> Color.White.copy(alpha = (alpha * scale).coerceIn(0f, 1f)) }

    // Tint light: the colour at its strip's outer edge, clear at the inner one.
    val tintStrip = { stripDp: Float, alpha: Float, top: Boolean ->
        val stripPx = GlassEdgeTokens.tintStripHeightDp(stripDp, heightDp).dp.toPx()
        val lit = edgeTint.copy(alpha = (edgeTint.alpha * alpha * scale).coerceIn(0f, 1f))
        val clear = edgeTint.copy(alpha = 0f)
        val y = if (top) 0f else size.height - stripPx
        Strip(
            brush =
                Brush.verticalGradient(
                    colors = if (top) listOf(lit, clear) else listOf(clear, lit),
                    startY = y,
                    endY = y + stripPx,
                ),
            topLeft = Offset(0f, y),
            size = Size(size.width, stripPx),
        )
    }
    val tintStrips =
        if (edgeTint.alpha > 0f && edgeTint != Color.Unspecified) {
            listOf(
                tintStrip(GlassEdgeTokens.TINT_TOP_STRIP_DP, GlassEdgeTokens.TINT_TOP_ALPHA, true),
                tintStrip(GlassEdgeTokens.TINT_BOTTOM_STRIP_DP, GlassEdgeTokens.TINT_BOTTOM_ALPHA, false),
            )
        } else {
            emptyList()
        }

    // Specular: one border width in from the top edge, a bell along its length like Serein's Hairline.
    val shoulder = GlassEdgeTokens.SPECULAR_SHOULDER_STOP
    val specular =
        Strip(
            brush =
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    shoulder to white(GlassEdgeTokens.SPECULAR_SHOULDER_ALPHA),
                    0.5f to white(GlassEdgeTokens.SPECULAR_PEAK_ALPHA),
                    (1f - shoulder) to white(GlassEdgeTokens.SPECULAR_SHOULDER_ALPHA),
                    1f to Color.Transparent,
                ),
            topLeft = Offset(0f, GlassEdgeTokens.BORDER_WIDTH_DP.dp.toPx()),
            size = Size(size.width, GlassEdgeTokens.SPECULAR_THICKNESS_DP.dp.toPx()),
        )

    val borderStops = GlassEdgeTokens.borderStops(heightDp, scale)
    return EdgeLights(
        tintStrips = tintStrips,
        specular = specular,
        border = shape.createOutline(size, layoutDirection, this),
        borderBrush =
            Brush.verticalGradient(
                colorStops =
                    borderStops
                        .mapIndexed { index, stop ->
                            stop.fraction to (if (index == 0) Color.White else shade).copy(alpha = stop.alpha)
                        }.toTypedArray(),
                startY = 0f,
                endY = size.height,
            ),
        borderStroke = Stroke(width = 2 * GlassEdgeTokens.BORDER_WIDTH_DP.dp.toPx()),
    )
}

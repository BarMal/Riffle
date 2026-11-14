package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
    /** Whether [edgeTint] also lights the bottom edge; a card's coloured glow is the top edge's only. */
    tintBottom: Boolean = true,
): Modifier {
    val reduced = LocalLiquidGlassSettings.current.reducedTransparency
    val tokens = GlassMaterialTokens.forWeight(weight)
    val seen = GlassEdgeTokens.seenLuminance(fill.luminance(), fill.alpha)
    val boost = GlassEdgeTokens.lightSurfaceBoost(seen)
    // The lit top edge is white; on light glass the shaded sides and bottom turn dark so they still read.
    val shadeMix = GlassEdgeTokens.lightness(seen) * GlassEdgeTokens.LIGHT_SURFACE_SHADE_MIX
    val shade = lerp(Color.White, Color.Black, shadeMix)
    val reducedScale = if (reduced) GlassMaterialTokens.REDUCED_TRANSPARENCY_EDGE_SCALE else 1f
    val scale = (tokens.edgeScale * boost * reducedScale).finiteOr(1f)
    return drawWithCache {
        // A surface with no area to light (0 x 0 while it is laid out away, a collapsed row) draws
        // no edge: the fill and the content are unaffected.
        val lights = if (size.isDrawable()) bakeEdgeLights(shape, scale, shade, edgeTint, tintBottom) else null
        onDrawWithContent {
            val fade = alphaProvider().finiteOr(1f).coerceIn(0f, 1f)
            if (lights != null && fade > 0f) lights.draw(this, fade)
            drawContent()
        }
    }
}

/** Whether a surface of this size can be given edge lights: both sides finite and at least one pixel. */
internal fun Size.isDrawable(): Boolean =
    width.isFinite() && height.isFinite() && width >= MIN_LIT_PX && height >= MIN_LIT_PX

private fun Float.finiteOr(fallback: Float): Float = if (isNaN() || isInfinite()) fallback else this

private const val MIN_LIT_PX = 1f

/** A hairline needs a little room under the border to sit in; a 3px-tall surface has none. */
private const val MIN_SPECULAR_HEIGHT_PX = 4f

/**
 * [shape]'s outline for a surface of [size], made safe to stroke: a rounded rectangle's radii are
 * clamped to half the shortest side (and to zero if not finite), and an outline whose bounds are not
 * finite or are empty falls back to the plain rectangle. Stroking a malformed path with a gradient
 * brush is the one drawing call here a software GPU could choke on, so it never gets one.
 */
internal fun safeEdgeOutline(
    shape: Shape,
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): Outline {
    val rectangle = Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
    val outline = shape.createOutline(size, layoutDirection, density)
    return when (outline) {
        is Outline.Rectangle -> if (outline.rect.isUsable()) outline else rectangle
        is Outline.Rounded -> {
            val round = outline.roundRect
            val limit = minOf(round.width, round.height) / 2f
            if (!round.isUsable()) {
                rectangle
            } else {
                fun clamp(radius: CornerRadius) =
                    CornerRadius(radius.x.finiteOr(0f).coerceIn(0f, limit), radius.y.finiteOr(0f).coerceIn(0f, limit))
                Outline.Rounded(
                    RoundRect(
                        round.left,
                        round.top,
                        round.right,
                        round.bottom,
                        clamp(round.topLeftCornerRadius),
                        clamp(round.topRightCornerRadius),
                        clamp(round.bottomRightCornerRadius),
                        clamp(round.bottomLeftCornerRadius),
                    ),
                )
            }
        }
        is Outline.Generic -> if (outline.path.getBounds().isUsable()) outline else rectangle
    }
}

private fun Rect.isUsable(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width >= MIN_LIT_PX && height >= MIN_LIT_PX

private fun RoundRect.isUsable(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width >= MIN_LIT_PX && height >= MIN_LIT_PX

/** A rectangle filled with a brush: one strip of light. */
private class Strip(
    val brush: Brush,
    val topLeft: Offset,
    val size: Size,
)

/**
 * One band of the border, top to bottom: the outline stroked in one solid [color] between [top] and
 * [bottom]. The border's vertical ramp (bright along the top, faint down the sides, fainter at the
 * bottom) is a few of these rather than one stroke with a gradient brush: on CI's software-rendered
 * emulator, a Dock holding icons whose edge lights drew the gradient stroke together with the
 * hairline took the whole emulator down (found by drawing the pieces one at a time on the device
 * job; each alone was harmless). Solid strokes draw the same ramp to within a step nobody can see.
 */
internal class BorderBand(
    val top: Float,
    val bottom: Float,
    val color: Color,
)

/**
 * The border ramp described by [stops] (fraction of [height] to colour, increasing) as solid bands:
 * a stretch between two stops of the same colour is one band, a stretch that changes is
 * [BORDER_RAMP_STEPS] bands, each the ramp's colour at its middle.
 */
internal fun borderBands(
    stops: List<Pair<Float, Color>>,
    height: Float,
): List<BorderBand> {
    if (stops.isEmpty() || !height.isFinite() || height <= 0f) return emptyList()
    return buildList {
        if (stops.size == 1) add(BorderBand(0f, height, stops[0].second))
        for (index in 0 until stops.size - 1) {
            val (fromFraction, fromColor) = stops[index]
            val (toFraction, toColor) = stops[index + 1]
            val from = fromFraction.coerceIn(0f, 1f) * height
            val to = toFraction.coerceIn(0f, 1f) * height
            if (to <= from) continue
            val steps = if (fromColor == toColor) 1 else BORDER_RAMP_STEPS
            for (step in 0 until steps) {
                val top = from + (to - from) * step / steps
                val bottom = from + (to - from) * (step + 1) / steps
                add(BorderBand(top, bottom, lerp(fromColor, toColor, (step + 0.5f) / steps)))
            }
        }
    }
}

/** Bands per changing stretch of the border ramp. */
private const val BORDER_RAMP_STEPS = 4

/** The edge lights as brushes over fixed rectangles and one outline, built once per size. */
private class EdgeLights(
    val tintStrips: List<Strip>,
    val specular: Strip?,
    val border: Outline,
    val borderBands: List<BorderBand>,
    val borderStroke: Stroke,
) {
    fun draw(
        scope: DrawScope,
        fade: Float,
    ) = with(scope) {
        tintStrips.forEach { strip -> drawRect(strip.brush, strip.topLeft, strip.size, alpha = fade) }
        specular?.let { strip -> drawRect(strip.brush, strip.topLeft, strip.size, alpha = fade) }
        // The border's ramp is drawn as solid strokes, one per band: see [BorderBand].
        val reach = borderStroke.width
        borderBands.forEach { band ->
            clipRect(left = -reach, top = band.top, right = size.width + reach, bottom = band.bottom) {
                drawOutline(border, band.color, alpha = fade, style = borderStroke)
            }
        }
    }
}

private fun CacheDrawScope.bakeEdgeLights(
    shape: Shape,
    scale: Float,
    shade: Color,
    edgeTint: Color,
    tintBottom: Boolean,
): EdgeLights {
    val heightDp = size.height / density
    val white = { alpha: Float -> Color.White.copy(alpha = (alpha * scale).coerceIn(0f, 1f)) }

    // Tint light: the colour at its strip's outer edge, clear at the inner one.
    val tintStrip = { stripDp: Float, alpha: Float, top: Boolean ->
        val stripPx = GlassEdgeTokens.tintStripHeightDp(stripDp, heightDp).dp.toPx().coerceAtMost(size.height)
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
        if (edgeTint != Color.Unspecified && edgeTint.alpha > 0f) {
            listOfNotNull(
                tintStrip(GlassEdgeTokens.TINT_TOP_STRIP_DP, GlassEdgeTokens.TINT_TOP_ALPHA, true),
                tintStrip(GlassEdgeTokens.TINT_BOTTOM_STRIP_DP, GlassEdgeTokens.TINT_BOTTOM_ALPHA, false)
                    .takeIf { tintBottom },
            ).filter { strip -> strip.size.height >= MIN_LIT_PX }
        } else {
            emptyList()
        }

    // Specular: one border width in from the top edge, a bell along its length like Serein's Hairline.
    val borderPx = GlassEdgeTokens.BORDER_WIDTH_DP.dp.toPx()
    val specularPx = GlassEdgeTokens.SPECULAR_THICKNESS_DP.dp.toPx()
    val shoulder = GlassEdgeTokens.SPECULAR_SHOULDER_STOP
    val specular =
        if (size.height >= MIN_SPECULAR_HEIGHT_PX + borderPx + specularPx) {
            Strip(
                brush =
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        shoulder to white(GlassEdgeTokens.SPECULAR_SHOULDER_ALPHA),
                        0.5f to white(GlassEdgeTokens.SPECULAR_PEAK_ALPHA),
                        (1f - shoulder) to white(GlassEdgeTokens.SPECULAR_SHOULDER_ALPHA),
                        1f to Color.Transparent,
                        startX = 0f,
                        endX = size.width,
                    ),
                topLeft = Offset(0f, borderPx),
                size = Size(size.width, specularPx),
            )
        } else {
            null
        }

    // The stops are strictly increasing by construction; nudge any pair that rounding made equal.
    val borderStops = GlassEdgeTokens.borderStops(heightDp, scale)
    val stopColors =
        Array(borderStops.size) { index ->
            val stop = borderStops[index]
            val previous = if (index == 0) -1f else borderStops[index - 1].fraction
            val fraction = if (index == 0) 0f else maxOf(stop.fraction, previous + MIN_STOP_GAP).coerceAtMost(1f)
            fraction to (if (index == 0) Color.White else shade).copy(alpha = stop.alpha.finiteOr(0f).coerceIn(0f, 1f))
        }
    return EdgeLights(
        tintStrips = tintStrips,
        specular = specular,
        border = safeEdgeOutline(shape, size, layoutDirection, this),
        borderBands = borderBands(stopColors.toList(), size.height),
        borderStroke = Stroke(width = (2 * borderPx).coerceAtLeast(MIN_LIT_PX)),
    )
}

/** Gradient stops closer than this are pushed apart, so no two share a position. */
private const val MIN_STOP_GAP = 1e-4f

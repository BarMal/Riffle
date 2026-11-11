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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockVisualEffect

/*
 * The dock's glass sheen: the layers the desktop glass theme's pills and panels draw over their
 * tint (`GlassBox.qml` / `GlassPillOverlay.qml`), so the dock reads as a lit pane of glass rather
 * than a flat fill.
 *
 *  1. Top specular   -- white, fading down from the top edge.
 *  2. Caustic spot   -- a soft white radial light, up and to the left of centre.
 *  3. Inner shadow   -- a faint darkening toward the bottom edge.
 *  4. Rim line       -- a 1dp highlight along the top, brightest in the middle, gone at the ends.
 *  5. Glass border   -- a 1dp cool-blue outline, the desktop's "chromatic" edge.
 *
 * The desktop's values are the baseline, tuned there for dark glass. [dockGlassSheenStrength]
 * scales them to the dock's own appearance: a light dock swallows white highlights, so they (and
 * the shadow that gives the pane its depth) strengthen as the dock gets lighter, the way the
 * desktop plugin's sheen adapts to the content under it. The border sits at the middle of the
 * desktop's 0.07..0.18 range.
 */

/** Multipliers on the desktop's baseline alphas, per group of layers. */
internal data class DockGlassSheenStrength(
    /** Specular, caustic spot and rim line. */
    val highlight: Float,
    /** Bottom inner shadow. */
    val shadow: Float,
    /** Glass border; 0 draws none. */
    val border: Float,
)

/**
 * How strongly the sheen is drawn on a dock filled with [surface] (its alpha already the dock's
 * background opacity) in the [effect] visual style.
 *
 * Lightness is what the eye actually gets through the dock: the fill's own luminance, weighted by
 * its opacity, over an assumed mid-dark wallpaper for the rest. So a clear (0%) dock is treated as
 * dark glass and gets the desktop's values, and a solid light dock gets the strongest treatment.
 *
 * - FLAT: the baseline.
 * - ELEVATED: brighter highlights and a deeper shadow -- a pane raised toward the light.
 * - OUTLINED: no glass border, since that style already draws its own outline.
 */
internal fun dockGlassSheenStrength(
    effect: DockVisualEffect,
    surface: Color,
): DockGlassSheenStrength {
    val opacity = surface.alpha.coerceIn(0f, 1f)
    val seenLuminance = surface.luminance() * opacity + ASSUMED_WALLPAPER_LUMINANCE * (1f - opacity)
    val lightness =
        ((seenLuminance - SHEEN_DARK_LUMINANCE) / (SHEEN_LIGHT_LUMINANCE - SHEEN_DARK_LUMINANCE)).coerceIn(0f, 1f)
    val highlight = 1f + (LIGHT_HIGHLIGHT_BOOST - 1f) * lightness
    val shadow = 1f + (LIGHT_SHADOW_BOOST - 1f) * lightness
    val border = 1f + (LIGHT_BORDER_BOOST - 1f) * lightness
    return when (effect) {
        DockVisualEffect.FLAT -> DockGlassSheenStrength(highlight, shadow, border)
        DockVisualEffect.ELEVATED ->
            DockGlassSheenStrength(highlight * ELEVATED_HIGHLIGHT_BOOST, shadow * ELEVATED_SHADOW_BOOST, border)
        DockVisualEffect.OUTLINED -> DockGlassSheenStrength(highlight, shadow, border = 0f)
    }
}

/**
 * The alpha a sheen layer is drawn at: its baseline, scaled by [scale] (the dock pull's fade times
 * the layer group's strength), and never past fully opaque.
 */
internal fun dockGlassSheenAlpha(
    layerAlpha: Float,
    scale: Float,
): Float = (layerAlpha * scale.coerceAtLeast(0f)).coerceAtMost(1f)

/**
 * Draws the glass sheen over this node's background and under its content. Chain it right after
 * the dock's background, which clips to [shape], so every layer follows the dock's corners.
 * [alphaProvider] is the dock pull's background fade, read at draw time. A no-op when liquid
 * glass is switched off, which keeps today's flat look for anyone who turned it off.
 *
 * The gradients are built once per size (and strength), not on every frame: the dock redraws on
 * every frame of a drag or pull, and the pull only changes the fade, applied as a plain alpha.
 */
@Composable
internal fun Modifier.dockGlassSheen(
    shape: Shape,
    strength: DockGlassSheenStrength,
    alphaProvider: () -> Float,
): Modifier {
    if (!LocalLiquidGlassSettings.current.enabled || !DOCK_GLASS_SHEEN_BISECT_ENABLED) return this
    return drawWithCache {
        val sheen = if (size.width > 0f && size.height > 0f) buildGlassSheen(shape, strength) else null
        onDrawWithContent {
            val fade = alphaProvider().coerceIn(0f, 1f)
            if (sheen != null && fade > 0f) drawGlassSheen(sheen, fade)
            drawContent()
        }
    }
}

/** One sheen layer: a brush filling a rect. */
private class SheenBand(
    val brush: Brush,
    val topLeft: Offset,
    val size: Size,
)

private class GlassSheen(
    val bands: List<SheenBand>,
    val border: Outline?,
    val borderColor: Color,
    val borderStroke: Stroke,
)

@Suppress("LongMethod")
private fun CacheDrawScope.buildGlassSheen(
    shape: Shape,
    strength: DockGlassSheenStrength,
): GlassSheen {
    val highlight = strength.highlight
    val white = { alpha: Float -> Color.White.copy(alpha = dockGlassSheenAlpha(alpha, highlight)) }
    val cornerPx = DOCK_SHEEN_REFERENCE_CORNER_DP.dp.toPx()
    val specularHeight = minOf(cornerPx * SPECULAR_CORNER_MULTIPLIER, size.height * SPECULAR_MAX_FRACTION)
    val shadowHeight = size.height * SHADOW_HEIGHT_FRACTION
    val rimPx = RIM_LINE_DP.dp.toPx()

    val bands =
        listOfNotNull(
            // 1. Top specular.
            SheenBand(
                brush =
                    Brush.verticalGradient(
                        0f to white(SPECULAR_TOP_ALPHA),
                        SPECULAR_MID_STOP to white(SPECULAR_MID_ALPHA),
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = specularHeight,
                    ),
                topLeft = Offset.Zero,
                size = Size(size.width, specularHeight),
            ),
            // 2. Caustic light spot.
            if (!DOCK_GLASS_SHEEN_BISECT_CAUSTIC) null else SheenBand(
                brush =
                    Brush.radialGradient(
                        0f to white(CAUSTIC_CENTER_ALPHA),
                        CAUSTIC_MID_STOP to white(CAUSTIC_MID_ALPHA),
                        1f to Color.Transparent,
                        center = Offset(size.width * CAUSTIC_X, size.height * CAUSTIC_Y),
                        radius = minOf(size.width, size.height) * CAUSTIC_RADIUS_FRACTION,
                    ),
                topLeft = Offset.Zero,
                size = size,
            ),
            // 3. Bottom inner shadow.
            SheenBand(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = dockGlassSheenAlpha(SHADOW_ALPHA, strength.shadow)),
                        startY = size.height - shadowHeight,
                        endY = size.height,
                    ),
                topLeft = Offset(0f, size.height - shadowHeight),
                size = Size(size.width, shadowHeight),
            ),
            // 4. Rim line: 1dp, one dp below the top edge, bell-shaped along its length.
            SheenBand(
                brush =
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        RIM_SHOULDER_STOP to white(RIM_SHOULDER_ALPHA),
                        0.5f to white(RIM_PEAK_ALPHA),
                        (1f - RIM_SHOULDER_STOP) to white(RIM_SHOULDER_ALPHA),
                        1f to Color.Transparent,
                    ),
                topLeft = Offset(0f, rimPx),
                size = Size(size.width, rimPx),
            ),
        )

    // 5. Glass border. Centred on the edge and clipped to the shape, so twice the width draws
    // 1dp inside -- the same trick dockSurfaceAppearance's own pull-branch outline uses.
    return GlassSheen(
        bands = bands,
        border =
            if (strength.border > 0f && DOCK_GLASS_SHEEN_BISECT_BORDER) {
                shape.createOutline(size, layoutDirection, this)
            } else {
                null
            },
        borderColor = GLASS_BORDER_COLOR.copy(alpha = dockGlassSheenAlpha(GLASS_BORDER_ALPHA, strength.border)),
        borderStroke = Stroke(width = 2 * GLASS_BORDER_DP.dp.toPx()),
    )
}

private fun DrawScope.drawGlassSheen(
    sheen: GlassSheen,
    fade: Float,
) {
    if (DOCK_GLASS_SHEEN_BISECT_FLAT) {
        drawRect(color = Color.White, alpha = BISECT_FLAT_ALPHA * fade)
        return
    }
    sheen.bands.forEach { band ->
        drawRect(brush = band.brush, topLeft = band.topLeft, size = band.size, alpha = fade)
    }
    sheen.border?.let { border ->
        drawOutline(outline = border, color = sheen.borderColor, alpha = fade, style = sheen.borderStroke)
    }
}

/**
 * The desktop sizes its specular from the pill's corner radius. The dock's own radius is a user
 * setting that can go to 0 (a square dock), which would make the specular vanish, so it is sized
 * from a fixed reference radius instead -- the desktop pill's own.
 */
/** TEMPORARY (Device verify bisect): which sheen layers are drawn, to isolate the emulator hang. */
private const val DOCK_GLASS_SHEEN_BISECT_ENABLED = true

/** TEMPORARY (Device verify bisect). */
private const val DOCK_GLASS_SHEEN_BISECT_CAUSTIC = false

/** TEMPORARY (Device verify bisect). */
private const val DOCK_GLASS_SHEEN_BISECT_BORDER = false

/** TEMPORARY (Device verify bisect): one flat fill instead of any gradient. */
private const val DOCK_GLASS_SHEEN_BISECT_FLAT = true

/** TEMPORARY (Device verify bisect). */
private const val BISECT_FLAT_ALPHA = 0.05f

private const val DOCK_SHEEN_REFERENCE_CORNER_DP = 16

private const val SPECULAR_CORNER_MULTIPLIER = 4f
private const val SPECULAR_MAX_FRACTION = 0.58f
private const val SPECULAR_TOP_ALPHA = 0.12f
private const val SPECULAR_MID_STOP = 0.40f
private const val SPECULAR_MID_ALPHA = 0.04f

private const val CAUSTIC_X = 0.30f
private const val CAUSTIC_Y = 0.20f
private const val CAUSTIC_RADIUS_FRACTION = 0.78f
private const val CAUSTIC_CENTER_ALPHA = 0.07f
private const val CAUSTIC_MID_STOP = 0.38f
private const val CAUSTIC_MID_ALPHA = 0.02f

private const val SHADOW_HEIGHT_FRACTION = 0.35f
private const val SHADOW_ALPHA = 0.09f

private const val RIM_LINE_DP = 1
private const val RIM_SHOULDER_STOP = 0.12f
private const val RIM_SHOULDER_ALPHA = 0.10f
private const val RIM_PEAK_ALPHA = 0.26f

/** Riffle's default wallpapers and most photos sit around here; used for the see-through share. */
private const val ASSUMED_WALLPAPER_LUMINANCE = 0.3f

/** Below this the dock reads as dark glass (desktop values); above the light one, fully light. */
private const val SHEEN_DARK_LUMINANCE = 0.3f
private const val SHEEN_LIGHT_LUMINANCE = 0.7f
private const val LIGHT_HIGHLIGHT_BOOST = 2.4f
private const val LIGHT_SHADOW_BOOST = 1.8f
private const val LIGHT_BORDER_BOOST = 1.6f
private const val ELEVATED_HIGHLIGHT_BOOST = 1.3f
private const val ELEVATED_SHADOW_BOOST = 1.5f

private val GLASS_BORDER_COLOR = Color(red = 0.55f, green = 0.66f, blue = 1.0f)
private const val GLASS_BORDER_DP = 1
private const val GLASS_BORDER_ALPHA = 0.125f

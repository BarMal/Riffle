package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 */
@Composable
internal fun Modifier.dockGlassSheen(
    shape: Shape,
    strength: DockGlassSheenStrength,
    alphaProvider: () -> Float,
): Modifier {
    if (!LocalLiquidGlassSettings.current.enabled) return this
    return drawWithContent {
        val fade = alphaProvider().coerceIn(0f, 1f)
        if (fade > 0f && size.width > 0f && size.height > 0f) {
            drawGlassSheen(shape, strength, fade)
        }
        drawContent()
    }
}

@Suppress("LongMethod")
private fun DrawScope.drawGlassSheen(
    shape: Shape,
    strength: DockGlassSheenStrength,
    fade: Float,
) {
    val highlight = fade * strength.highlight
    val rimShoulder = Color.White.copy(alpha = dockGlassSheenAlpha(RIM_SHOULDER_ALPHA, highlight))
    val shadow = fade * strength.shadow
    val border = fade * strength.border
    val cornerPx = DOCK_SHEEN_REFERENCE_CORNER_DP.dp.toPx()

    // 1. Top specular.
    val specularHeight = minOf(cornerPx * SPECULAR_CORNER_MULTIPLIER, size.height * SPECULAR_MAX_FRACTION)
    drawRect(
        brush =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = dockGlassSheenAlpha(SPECULAR_TOP_ALPHA, highlight)),
                SPECULAR_MID_STOP to Color.White.copy(alpha = dockGlassSheenAlpha(SPECULAR_MID_ALPHA, highlight)),
                1f to Color.Transparent,
                startY = 0f,
                endY = specularHeight,
            ),
        size = Size(size.width, specularHeight),
    )

    // 2. Caustic light spot.
    val causticCenter = Offset(size.width * CAUSTIC_X, size.height * CAUSTIC_Y)
    drawRect(
        brush =
            Brush.radialGradient(
                0f to Color.White.copy(alpha = dockGlassSheenAlpha(CAUSTIC_CENTER_ALPHA, highlight)),
                CAUSTIC_MID_STOP to Color.White.copy(alpha = dockGlassSheenAlpha(CAUSTIC_MID_ALPHA, highlight)),
                1f to Color.Transparent,
                center = causticCenter,
                radius = minOf(size.width, size.height) * CAUSTIC_RADIUS_FRACTION,
            ),
    )

    // 3. Bottom inner shadow.
    val shadowHeight = size.height * SHADOW_HEIGHT_FRACTION
    drawRect(
        brush =
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = dockGlassSheenAlpha(SHADOW_ALPHA, shadow)),
                startY = size.height - shadowHeight,
                endY = size.height,
            ),
        topLeft = Offset(0f, size.height - shadowHeight),
        size = Size(size.width, shadowHeight),
    )

    // 4. Rim line: 1dp, one dp below the top edge, bell-shaped along its length.
    val rimPx = RIM_LINE_DP.dp.toPx()
    drawRect(
        brush =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                RIM_SHOULDER_STOP to rimShoulder,
                0.5f to Color.White.copy(alpha = dockGlassSheenAlpha(RIM_PEAK_ALPHA, highlight)),
                (1f - RIM_SHOULDER_STOP) to rimShoulder,
                1f to Color.Transparent,
            ),
        topLeft = Offset(0f, rimPx),
        size = Size(size.width, rimPx),
    )

    // 5. Glass border. Centred on the edge and clipped to the shape, so twice the width draws
    // 1dp inside -- the same trick dockSurfaceAppearance's own pull-branch outline uses.
    if (border > 0f) {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = GLASS_BORDER_COLOR.copy(alpha = dockGlassSheenAlpha(GLASS_BORDER_ALPHA, border)),
            style = Stroke(width = 2 * GLASS_BORDER_DP.dp.toPx()),
        )
    }
}

/**
 * The desktop sizes its specular from the pill's corner radius. The dock's own radius is a user
 * setting that can go to 0 (a square dock), which would make the specular vanish, so it is sized
 * from a fixed reference radius instead -- the desktop pill's own.
 */
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

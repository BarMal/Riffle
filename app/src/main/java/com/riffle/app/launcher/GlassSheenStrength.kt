package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import kotlin.math.ceil

/*
 * The glass sheen: the layers the desktop glass theme's pills and panels draw over their tint
 * (`GlassBox.qml` / `GlassPillOverlay.qml`), so a surface reads as a lit pane of glass rather than
 * a flat fill. Part of every `GlassSurface` weight, and of the dock's own background.
 *
 *  1. Top specular   -- white, fading down from the top edge.
 *  2. Caustic spot   -- a soft white radial light, up and to the left of centre.
 *  3. Inner shadow   -- a faint darkening toward the bottom edge.
 *  4. Rim line       -- a 1dp highlight along the top, brightest in the middle, gone at the ends.
 *  5. Glass border   -- a 1dp cool-blue outline, the desktop's "chromatic" edge.
 *
 * The desktop's values are the baseline, tuned there for dark glass. [glassSheenStrength]
 * scales them to the surface's own appearance: a light surface swallows white highlights, so they
 * (and the shadow that gives the pane its depth) strengthen as the surface gets lighter, the way
 * the desktop plugin's sheen adapts to the content under it. The border sits at the middle of the
 * desktop's 0.07..0.18 range.
 */

/** Multipliers on the desktop's baseline alphas, per group of layers. */
internal data class GlassSheenStrength(
    /** Specular, caustic spot and rim line. */
    val highlight: Float,
    /** Bottom inner shadow. */
    val shadow: Float,
    /** Glass border; 0 draws none. */
    val border: Float,
) {
    /** Every group scaled by [factor], for a weight's own strength or a faint reduced-transparency sheen. */
    fun scaled(factor: Float): GlassSheenStrength =
        GlassSheenStrength(highlight * factor, shadow * factor, border * factor)

    /** Each group scaled by its own factor, for a weight's highlight, shadow and hairline scales. */
    fun scaled(
        highlight: Float,
        shadow: Float,
        border: Float,
    ): GlassSheenStrength = GlassSheenStrength(this.highlight * highlight, this.shadow * shadow, this.border * border)
}

/**
 * How strongly the sheen is drawn on a surface filled with [surface] (its alpha already the
 * surface's background opacity).
 *
 * Lightness is what the eye actually gets through the surface: the fill's own luminance, weighted
 * by its opacity, over an assumed mid-dark wallpaper for the rest. So a clear (0%) surface is
 * treated as dark glass and gets the desktop's values, and a solid light one gets the strongest
 * treatment.
 */
internal fun glassSheenStrength(surface: Color): GlassSheenStrength {
    val opacity = surface.alpha.coerceIn(0f, 1f)
    val seenLuminance = surface.luminance() * opacity + ASSUMED_WALLPAPER_LUMINANCE * (1f - opacity)
    val lightness =
        ((seenLuminance - SHEEN_DARK_LUMINANCE) / (SHEEN_LIGHT_LUMINANCE - SHEEN_DARK_LUMINANCE)).coerceIn(0f, 1f)
    return GlassSheenStrength(
        highlight = 1f + (LIGHT_HIGHLIGHT_BOOST - 1f) * lightness,
        shadow = 1f + (LIGHT_SHADOW_BOOST - 1f) * lightness,
        border = 1f + (LIGHT_BORDER_BOOST - 1f) * lightness,
    )
}

/**
 * The alpha a sheen layer is drawn at: its baseline, scaled by [scale] (the dock pull's fade times
 * the layer group's strength), and never past fully opaque.
 */
internal fun glassSheenAlpha(
    layerAlpha: Float,
    scale: Float,
): Float = (layerAlpha * scale.coerceAtLeast(0f)).coerceAtMost(1f)

/**
 * Draws the glass sheen over this node's background and under its content. Chain it right after
 * the surface's background, which clips to [shape], so every layer follows its corners.
 * [alphaProvider] is the surface's own fade (the dock pull's background fade), read at draw time.
 * A no-op when liquid glass is switched off, which keeps today's flat look for anyone who turned
 * it off. Under reduced transparency the sheen is kept, but faint.
 *
 * The gradients are baked in software into small images once per size and strength -- the same
 * approach the desktop plugin takes with its edge textures -- and the GPU only stretches those
 * images. Live gradient shaders on the dock hung the software-rendered CI emulator within a couple
 * of minutes of UI tests, and baked ramps are cheaper on any device besides: a few kilobytes per
 * dock, no shader work per frame, and the dock pull's fade is just a draw alpha.
 */
@Composable
internal fun Modifier.glassSheen(
    shape: Shape,
    strength: GlassSheenStrength,
    alphaProvider: () -> Float = { 1f },
): Modifier {
    val glass = LocalLiquidGlassSettings.current
    if (!glass.enabled) return this
    val effective =
        if (glass.reducedTransparency) {
            strength.scaled(GlassMaterialTokens.REDUCED_TRANSPARENCY_SHEEN_SCALE)
        } else {
            strength
        }
    return drawWithCache {
        val sheen = if (size.width >= 1f && size.height >= 1f) bakeGlassSheen(shape, effective) else null
        onDrawWithContent {
            val fade = alphaProvider().coerceIn(0f, 1f)
            if (sheen != null && fade > 0f) drawGlassSheen(sheen, fade)
            drawContent()
        }
    }
}

/** One baked layer: a small image, stretched over [size] at [offset]. */
private class SheenImage(
    val image: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize,
)

/** The sheen, baked: small images stretched over the surface, plus the border outline. */
private class GlassSheen(
    val layers: List<SheenImage>,
    val border: Outline?,
    val borderColor: Color,
    val borderStroke: Stroke,
)

private fun CacheDrawScope.bakeGlassSheen(
    shape: Shape,
    strength: GlassSheenStrength,
): GlassSheen {
    val white = { alpha: Float -> Color.White.copy(alpha = glassSheenAlpha(alpha, strength.highlight)) }
    val widthPx = ceil(size.width).toInt()
    val heightPx = ceil(size.height).toInt()
    val rimPx = RIM_LINE_DP.dp.roundToPx().coerceAtLeast(1)

    // 1 + 3. Top specular and bottom inner shadow share one vertical ramp.
    val specularHeight = minOf(SPECULAR_MAX_HEIGHT_DP.dp.toPx(), heightPx * SPECULAR_MAX_FRACTION)
    val shadowHeight = heightPx * SHADOW_HEIGHT_FRACTION
    val verticalRamp =
        bake(width = 1, height = heightPx) {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to white(SPECULAR_TOP_ALPHA),
                        SPECULAR_MID_STOP to white(SPECULAR_MID_ALPHA),
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = specularHeight,
                    ),
                size = Size(1f, specularHeight),
            )
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = glassSheenAlpha(SHADOW_ALPHA, strength.shadow)),
                        startY = heightPx - shadowHeight,
                        endY = heightPx.toFloat(),
                    ),
                topLeft = Offset(0f, heightPx - shadowHeight),
                size = Size(1f, shadowHeight),
            )
        }

    // 4. Rim line: bell-shaped along its length, one rim width below the top edge.
    val rimRamp =
        bake(width = widthPx, height = 1) {
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        RIM_SHOULDER_STOP to white(RIM_SHOULDER_ALPHA),
                        0.5f to white(RIM_PEAK_ALPHA),
                        (1f - RIM_SHOULDER_STOP) to white(RIM_SHOULDER_ALPHA),
                        1f to Color.Transparent,
                    ),
            )
        }

    // 2. Caustic light spot, baked small and stretched back up.
    val causticWidth = ceil(widthPx / CAUSTIC_BAKE_SCALE).toInt().coerceAtLeast(1)
    val causticHeight = ceil(heightPx / CAUSTIC_BAKE_SCALE).toInt().coerceAtLeast(1)
    val caustic =
        bake(width = causticWidth, height = causticHeight) {
            drawRect(
                brush =
                    Brush.radialGradient(
                        0f to white(CAUSTIC_CENTER_ALPHA),
                        CAUSTIC_MID_STOP to white(CAUSTIC_MID_ALPHA),
                        1f to Color.Transparent,
                        center = Offset(causticWidth * CAUSTIC_X, causticHeight * CAUSTIC_Y),
                        radius = minOf(causticWidth, causticHeight) * CAUSTIC_RADIUS_FRACTION,
                    ),
            )
        }

    // 5. Glass border: a plain stroke, nothing to bake. Centred on the edge and clipped to the
    // shape, so twice the width draws 1dp inside -- the same trick dockSurfaceAppearance's own
    // pull-branch outline uses.
    val surfaceSize = IntSize(widthPx, heightPx)
    return GlassSheen(
        layers =
            listOf(
                // 1px wide, the surface's height: top specular and bottom inner shadow.
                SheenImage(verticalRamp, IntOffset.Zero, surfaceSize),
                // At a fraction of the surface's resolution: a soft blur either way.
                SheenImage(caustic, IntOffset.Zero, surfaceSize),
                // The surface's width, 1px tall: the rim line's bell curve.
                SheenImage(rimRamp, IntOffset(0, rimPx), IntSize(widthPx, rimPx)),
            ),
        border = if (strength.border > 0f) shape.createOutline(size, layoutDirection, this) else null,
        borderColor = GLASS_BORDER_COLOR.copy(alpha = glassSheenAlpha(GLASS_BORDER_ALPHA, strength.border)),
        borderStroke = Stroke(width = 2 * GLASS_BORDER_DP.dp.toPx()),
    )
}

/** Renders [block] into a new [width] x [height] image on the CPU, in software. */
private fun CacheDrawScope.bake(
    width: Int,
    height: Int,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = this,
        layoutDirection = layoutDirection,
        canvas = Canvas(image),
        size = Size(width.toFloat(), height.toFloat()),
        block = block,
    )
    return image
}

private fun DrawScope.drawGlassSheen(
    sheen: GlassSheen,
    fade: Float,
) {
    sheen.layers.forEach { layer ->
        drawImage(
            image = layer.image,
            dstOffset = layer.offset,
            dstSize = layer.size,
            alpha = fade,
            filterQuality = FilterQuality.Low,
        )
    }
    sheen.border?.let { border ->
        drawOutline(outline = border, color = sheen.borderColor, alpha = fade, style = sheen.borderStroke)
    }
}

/**
 * The desktop sizes its specular from the pill's corner radius (4 x 16dp). A surface's own radius
 * can go to 0 (a square dock, a flat sheet), which would make the specular vanish, so it is sized
 * from the surface's height instead, and only tall surfaces hit this cap -- the desktop pill's own.
 */
private const val SPECULAR_MAX_HEIGHT_DP = 64

private const val SPECULAR_MAX_FRACTION = 0.58f
private const val SPECULAR_TOP_ALPHA = 0.12f
private const val SPECULAR_MID_STOP = 0.40f
private const val SPECULAR_MID_ALPHA = 0.04f

/** The caustic is baked at 1/8 of the surface's resolution: a soft radial loses nothing to it. */
private const val CAUSTIC_BAKE_SCALE = 8f

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

private val GLASS_BORDER_COLOR = Color(red = 0.55f, green = 0.66f, blue = 1.0f)
private const val GLASS_BORDER_DP = 1
private const val GLASS_BORDER_ALPHA = 0.125f

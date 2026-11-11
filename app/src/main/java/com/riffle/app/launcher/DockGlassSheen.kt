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
import androidx.compose.ui.unit.dp

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
 * Desktop values are kept as-is, except the border: the desktop's border "breathes" between 0.07
 * and 0.18 alpha. A launcher's dock is on screen all the time, so a never-ending animation here
 * would cost battery for very little, and the border sits still at the middle of that range.
 */

/** The alpha a sheen layer is drawn at: its own, scaled by the dock pull's background fade. */
internal fun dockGlassSheenAlpha(
    layerAlpha: Float,
    fade: Float,
): Float = layerAlpha * fade.coerceIn(0f, 1f)

/**
 * Draws the glass sheen over this node's background and under its content. Chain it right after
 * the dock's background, which clips to [shape], so every layer follows the dock's corners.
 * [alphaProvider] is the dock pull's background fade, read at draw time. A no-op when liquid
 * glass is switched off, which keeps today's flat look for anyone who turned it off.
 */
@Composable
internal fun Modifier.dockGlassSheen(
    shape: Shape,
    alphaProvider: () -> Float,
): Modifier {
    if (!LocalLiquidGlassSettings.current.enabled) return this
    return drawWithContent {
        val fade = alphaProvider().coerceIn(0f, 1f)
        if (fade > 0f && size.width > 0f && size.height > 0f) {
            drawGlassSheen(shape, fade)
        }
        drawContent()
    }
}

private fun DrawScope.drawGlassSheen(
    shape: Shape,
    fade: Float,
) {
    val cornerPx = DOCK_SHEEN_REFERENCE_CORNER_DP.dp.toPx()

    // 1. Top specular.
    val specularHeight = minOf(cornerPx * SPECULAR_CORNER_MULTIPLIER, size.height * SPECULAR_MAX_FRACTION)
    drawRect(
        brush =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = dockGlassSheenAlpha(SPECULAR_TOP_ALPHA, fade)),
                SPECULAR_MID_STOP to Color.White.copy(alpha = dockGlassSheenAlpha(SPECULAR_MID_ALPHA, fade)),
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
                0f to Color.White.copy(alpha = dockGlassSheenAlpha(CAUSTIC_CENTER_ALPHA, fade)),
                CAUSTIC_MID_STOP to Color.White.copy(alpha = dockGlassSheenAlpha(CAUSTIC_MID_ALPHA, fade)),
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
                1f to Color.Black.copy(alpha = dockGlassSheenAlpha(SHADOW_ALPHA, fade)),
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
                RIM_SHOULDER_STOP to Color.White.copy(alpha = dockGlassSheenAlpha(RIM_SHOULDER_ALPHA, fade)),
                0.5f to Color.White.copy(alpha = dockGlassSheenAlpha(RIM_PEAK_ALPHA, fade)),
                (1f - RIM_SHOULDER_STOP) to Color.White.copy(alpha = dockGlassSheenAlpha(RIM_SHOULDER_ALPHA, fade)),
                1f to Color.Transparent,
            ),
        topLeft = Offset(0f, rimPx),
        size = Size(size.width, rimPx),
    )

    // 5. Glass border. Centred on the edge and clipped to the shape, so twice the width draws
    // 1dp inside -- the same trick dockSurfaceAppearance's own pull-branch outline uses.
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = GLASS_BORDER_COLOR.copy(alpha = dockGlassSheenAlpha(GLASS_BORDER_ALPHA, fade)),
        style = Stroke(width = 2 * GLASS_BORDER_DP.dp.toPx()),
    )
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

private val GLASS_BORDER_COLOR = Color(red = 0.55f, green = 0.66f, blue = 1.0f)
private const val GLASS_BORDER_DP = 1
private const val GLASS_BORDER_ALPHA = 0.125f

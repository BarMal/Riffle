package com.riffle.app.launcher

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The one glass material shared by every "glassy" surface in the polish pass (the card-stack
 * header pill and its floating action capsule today; the settings-sheet redesign in a parallel PR
 * is expected to reuse this same primitive rather than growing its own -- keep this file minimal
 * and self-contained so the two land without colliding).
 *
 * There is no real backdrop sample here: Compose has no first-party "blur everything already drawn
 * behind this layer" primitive short of capturing a [androidx.compose.ui.graphics.layer.GraphicsLayer]
 * of the whole screen, which is more machinery than a header pill and an action capsule justify. On
 * API 31+ this instead runs the panel's own translucent tint through [RenderEffect.createBlurEffect],
 * which still softens whatever shows through the tint's alpha and reads as frosted glass in practice;
 * below API 31 the panel is a flatter, more opaque scrim with the same tint and hairline so it never
 * looks like a broken effect, only a plainer one.
 */
@Composable
fun Modifier.glassPanel(shape: Shape = MaterialTheme.shapes.large): Modifier {
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val tint =
        MaterialTheme.colorScheme.surface.copy(
            alpha = if (supportsBlur) GLASS_TINT_ALPHA_BLURRED else GLASS_TINT_ALPHA_FALLBACK,
        )
    return this
        .clip(shape)
        .then(if (supportsBlur) Modifier.graphicsLayer { renderEffect = glassBlurEffect() } else Modifier)
        .background(tint)
        .glassHairline()
}

/** The 1dp specular highlight along the panel's top edge that reads as a glass rim. */
private fun Modifier.glassHairline(): Modifier =
    drawWithContent {
        drawContent()
        drawLine(
            color = Color.White.copy(alpha = GLASS_HAIRLINE_ALPHA),
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = GLASS_HAIRLINE_WIDTH_DP.dp.toPx(),
        )
    }

private fun glassBlurEffect() =
    RenderEffect
        .createBlurEffect(GLASS_BLUR_RADIUS_PX, GLASS_BLUR_RADIUS_PX, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()

/**
 * A ready-made glass container for callers that want the panel and its content in one call, rather
 * than composing [Modifier.glassPanel] onto their own container.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.glassPanel(shape)) {
        content()
    }
}

private const val GLASS_TINT_ALPHA_BLURRED = 0.55f
private const val GLASS_TINT_ALPHA_FALLBACK = 0.75f
private const val GLASS_HAIRLINE_ALPHA = 0.12f
private const val GLASS_HAIRLINE_WIDTH_DP = 1
private const val GLASS_BLUR_RADIUS_PX = 24f

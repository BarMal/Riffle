package com.riffle.app.launcher

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Riffle's glass material: a tinted, blurred layer with a 1dp top hairline highlight, used for
 * floating chrome (sheets, pills) so it reads as a distinct surface above whatever sits behind it
 * rather than a flat, abruptly-cut card.
 *
 * True backdrop blur -- sampling the pixels actually behind this layer -- needs a captured render
 * node that a plain composable cannot get to. This blurs the tint layer itself instead, which reads
 * the same way for a translucent scrim and degrades gracefully: below API 31,
 * [android.graphics.RenderEffect] does not exist, so the fallback is a stronger solid scrim with no
 * blur at all, keeping the same tint and hairline so the surface never looks unfinished.
 *
 * [content] is drawn on top of the glass layer, clipped to the same [shape].
 */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit = {},
) {
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val tintAlpha = if (supportsBlur) GLASS_TINT_ALPHA else GLASS_FALLBACK_SCRIM_ALPHA

    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .then(if (supportsBlur) Modifier.blur(GLASS_BLUR_RADIUS_DP.dp) else Modifier)
                    .background(tint.copy(alpha = tintAlpha)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GLASS_HIGHLIGHT_HEIGHT_DP.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = GLASS_HIGHLIGHT_ALPHA)),
        )
        content()
    }
}

private const val GLASS_TINT_ALPHA = 0.55f
private const val GLASS_FALLBACK_SCRIM_ALPHA = 0.75f
private const val GLASS_BLUR_RADIUS_DP = 24
private const val GLASS_HIGHLIGHT_HEIGHT_DP = 1
private const val GLASS_HIGHLIGHT_ALPHA = 0.12f

package com.riffle.app.launcher

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * The shared backdrop every liquid-glass surface on Home samples from: a live recording of
 * whatever draws beneath it (wallpaper + Home content), re-captured every frame that content
 * redraws. Null below API 33 (no [android.graphics.RuntimeShader] to feed it into) or wherever
 * [ProvideLiquidGlassBackdrop] has not been installed above the reader in the composition.
 *
 * A single shared [GraphicsLayer] rather than one per glass surface: every glass surface on Home
 * sits over the same content, so one recording, taken once near Home's root, serves all of them.
 * `GlassSurface` never mutates this layer directly -- see its own doc comment for how it derives a
 * private, per-surface crop from it instead, so multiple glass surfaces reading it in the same
 * frame never race on a shared [GraphicsLayer.renderEffect].
 */
internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Installs the shared backdrop capture for [content] and provides it as [LocalLiquidGlassBackdrop]
 * to every descendant, including [content] itself.
 *
 * This is a foundation-slice simplification, not a general-purpose "capture the whole window"
 * mechanism: it only records what [content] itself draws. That is exactly right for the 3 call
 * sites this PR wires up (the Cards dock header pill/capsule and the appearance-tuning sheet all
 * sit inside [HomeDestination]'s own content), but a glass surface placed over content outside this
 * subtree -- there are none yet -- would not see it behind it. Below API 33 this is a no-op:
 * [content] runs directly, with no layer recorded, matching [GlassSurface]'s own fallback gate.
 */
@Composable
internal fun ProvideLiquidGlassBackdrop(content: @Composable (backdropCapture: Modifier) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val backdropLayer = rememberGraphicsLayer()
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdropLayer) {
            content(Modifier.recordLiquidGlassBackdrop(backdropLayer))
        }
    } else {
        content(Modifier)
    }
}

/**
 * Records this modifier's node's own drawn content into [layer] every time it redraws, in addition
 * to drawing it on screen exactly as before -- the recording is purely an extra side channel for
 * [LocalLiquidGlassBackdrop] readers, so this never changes what actually appears here.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.recordLiquidGlassBackdrop(layer: GraphicsLayer): Modifier =
    drawWithContent {
        layer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(size.width.toInt(), size.height.toInt()),
        ) {
            this@drawWithContent.drawContent()
        }
        drawContent()
    }

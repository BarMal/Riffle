package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect

internal data class DockAppearance(
    val elevationDp: Int,
    val outlineWidthDp: Int,
    val cornerRadiusDp: Int,
)

internal fun dockAppearanceSpec(
    effect: DockVisualEffect,
    cornerRadiusDp: Int,
): DockAppearance =
    when (effect) {
        DockVisualEffect.FLAT ->
            DockAppearance(elevationDp = 0, outlineWidthDp = 0, cornerRadiusDp = cornerRadiusDp)
        DockVisualEffect.ELEVATED ->
            DockAppearance(elevationDp = 6, outlineWidthDp = 0, cornerRadiusDp = cornerRadiusDp)
        DockVisualEffect.OUTLINED ->
            DockAppearance(elevationDp = 0, outlineWidthDp = 1, cornerRadiusDp = cornerRadiusDp)
    }

@Composable
internal fun dockBaseSurfaceColor(dock: DockModel): Color =
    when (dock.visualEffect) {
        DockVisualEffect.ELEVATED -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
internal fun dockSurfaceColor(dock: DockModel): Color {
    val baseColor = dockBaseSurfaceColor(dock)
    val selectedColor = LocalLauncherThemeColorOverrides.current.dock ?: baseColor
    return selectedColor.copy(alpha = selectedColor.alpha * dock.backgroundAlphaPercent / 100f)
}

/** A dock background at its own alpha: what every dock outside a dock pull draws. */
internal val OpaqueDockBackground: () -> Float = { 1f }

/**
 * The multiplier the dock pull applies to the dock background's alpha (Decision 4), read at draw
 * time so a pull animates the background without recomposing the dock.
 */
internal val LocalDockBackgroundAlpha = staticCompositionLocalOf { OpaqueDockBackground }

@Composable
internal fun Modifier.dockSurfaceAppearance(dock: DockModel): Modifier {
    val spec = dockAppearanceSpec(dock.visualEffect, dock.cornerRadiusDp)
    val shape = RoundedCornerShape(spec.cornerRadiusDp.dp)
    val backgroundAlpha = LocalDockBackgroundAlpha.current
    if (backgroundAlpha === OpaqueDockBackground) {
        var result = this
        if (spec.elevationDp > 0) result = result.shadow(spec.elevationDp.dp, shape)
        result = result.clip(shape).background(dockSurfaceColor(dock))
        if (spec.outlineWidthDp > 0) {
            result = result.border(spec.outlineWidthDp.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        }
        return result
    }
    // Under a dock pull: the same shadow, fill and outline, each faded by the pull at draw time.
    val color = dockSurfaceColor(dock)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val elevationDp = spec.elevationDp
    val outlineWidthDp = spec.outlineWidthDp
    return graphicsLayer {
        shadowElevation = elevationDp.dp.toPx() * backgroundAlpha()
        this.shape = shape
        clip = true
    }.drawBehind {
        val alpha = backgroundAlpha().coerceIn(0f, 1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = color.copy(alpha = color.alpha * alpha))
        if (outlineWidthDp > 0) {
            drawOutline(
                outline,
                color = outlineColor.copy(alpha = outlineColor.alpha * alpha),
                // Centred on the edge and clipped to the shape, so twice as wide draws the same
                // inside-only width as Modifier.border.
                style = Stroke(width = 2 * outlineWidthDp.dp.toPx()),
            )
        }
    }
}

/**
 * Draws a fading-in ring around the dock while a home-grid item is being dragged over it, so the
 * dock's own drop zone is visible before the drag is released instead of only afterward.
 */
@Composable
internal fun Modifier.dockDropHighlight(
    dock: DockModel,
    isHighlighted: Boolean,
    reducedMotion: Boolean = false,
): Modifier {
    val alpha by
        animateFloatAsState(
            targetValue = if (isHighlighted) 1f else 0f,
            animationSpec = if (reducedMotion) snap() else tween(DOCK_DROP_HIGHLIGHT_ANIMATION_MILLIS),
            label = "dockDropHighlightAlpha",
        )
    if (alpha <= 0f) return this
    val shape = RoundedCornerShape(dockAppearanceSpec(dock.visualEffect, dock.cornerRadiusDp).cornerRadiusDp.dp)
    return border(DOCK_DROP_HIGHLIGHT_WIDTH_DP.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
}

private const val DOCK_DROP_HIGHLIGHT_WIDTH_DP = 2
private const val DOCK_DROP_HIGHLIGHT_ANIMATION_MILLIS = 120

/**
 * A [RenderEffect] blurring by [strength] (0..1) of [maxRadiusPx], or null once that scales to
 * nothing -- [RenderEffect.createBlurEffect] (which [BlurEffect] wraps) rejects a zero/negative
 * radius, and null is also cheaper than a same-as-no-op blur while the pull is at rest.
 */
private fun reorientBlurEffect(
    strength: Float,
    maxRadiusPx: Float,
): RenderEffect? {
    val radiusPx = maxRadiusPx * strength.coerceIn(0f, 1f)
    return if (radiusPx > 0f) BlurEffect(radiusPx, radiusPx, TileMode.Clamp) else null
}

/**
 * The dock re-orientation's "iPhone Duo" frost (dock-reorient decisions, follow-up to #1278): a
 * blur and darken scrim over the dock, both continuously proportional to [strengthProvider] (0 at
 * rest, 1 at the frost's strongest -- see
 * [com.riffle.core.domain.launcher.dockpull.dockPullReorientFrostStrength]), read inside a
 * [graphicsLayer]/draw block so both animate without recomposing, exactly like
 * [dockSurfaceAppearance]'s own pull branch. [strengthProvider] is only called while [isActive] --
 * pass the dock pull's `transitioning` flag, which changes just at a pull's start and end, not
 * [strengthProvider]'s value.
 *
 * The blur is a real, [RenderEffect]-backed one set directly on
 * [androidx.compose.ui.graphics.GraphicsLayerScope.renderEffect] rather than via
 * [androidx.compose.ui.draw.blur] -- that modifier fixes its radius when the
 * modifier chain is built, not read lazily like a draw block, so it could only step the blur on at
 * full strength rather than ramp it in with the drag; a [graphicsLayer] block, like the darken
 * scrim's own draw block, is invoked every frame the layer redraws, so the radius rides
 * [strengthProvider] exactly like the darken alpha does. minSdk is 31 (Android 12), so
 * [RenderEffect] is always available here, with no pre-31 darken-only fallback.
 */
internal fun Modifier.dockReorientFrost(
    isActive: Boolean,
    strengthProvider: () -> Float,
): Modifier {
    if (!isActive) return this
    return graphicsLayer {
        renderEffect = reorientBlurEffect(strengthProvider(), DOCK_REORIENT_MAX_BLUR_DP.dp.toPx())
        clip = true
    }.drawWithContent {
        drawContent()
        val strength = strengthProvider().coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(color = Color.Black.copy(alpha = DOCK_REORIENT_MAX_DARKEN_ALPHA * strength))
        }
    }
}

private const val DOCK_REORIENT_MAX_BLUR_DP = 12
private const val DOCK_REORIENT_MAX_DARKEN_ALPHA = 0.35f

/**
 * The whole-screen version of [dockReorientFrost] (extension to the "iPhone Duo" reference, follow-up
 * to #1279): instead of darkening the dock's own chrome, two edge bands -- fully dark right at the
 * edge, fading to transparent -- grow in from the two screen edges perpendicular to the pull as
 * [edgeBandFractionProvider] deepens (see
 * [dockPullReorientEdgeBandFraction][com.riffle.core.domain.launcher.dockpull.dockPullReorientEdgeBandFraction]),
 * and recede back out as the pull settles -- like curtains closing in around the fold and opening
 * back out. Capped well short of meeting in the middle, so the centre of the screen always stays
 * legible even at a full pull. [isVerticalPull] picks which pair of edges: top/bottom for a
 * bottom/top dock's vertical pull, left/right for a side dock's horizontal one.
 *
 * Applied to the Box that wraps *both* mode surfaces, never the dock itself -- the dock is the
 * handle you're holding, so it stays sharp throughout while the screen around it recedes. The
 * blur rides [strengthProvider] (0..1, the same progress [dockReorientFrost] reads) via a
 * [graphicsLayer]'s [androidx.compose.ui.graphics.GraphicsLayerScope.renderEffect] rather than
 * [androidx.compose.ui.draw.blur], for the same reason documented on [dockReorientFrost]: only
 * that lets the radius ramp in with the drag instead of stepping straight to full strength. The
 * darken bands keep their own [edgeBandFractionProvider], since that one also picks each band's
 * width, not just an intensity.
 */
internal fun Modifier.screenReorientFrost(
    isActive: Boolean,
    isVerticalPull: Boolean,
    strengthProvider: () -> Float,
    edgeBandFractionProvider: () -> Float,
): Modifier {
    if (!isActive) return this
    return graphicsLayer {
        renderEffect = reorientBlurEffect(strengthProvider(), SCREEN_REORIENT_MAX_BLUR_DP.dp.toPx())
        clip = true
    }.drawWithContent {
        drawContent()
        val bandFraction = edgeBandFractionProvider().coerceIn(0f, 0.5f)
        if (bandFraction > 0f) {
            val edgeColor = Color.Black.copy(alpha = SCREEN_REORIENT_EDGE_DARKEN_ALPHA)
            val stops =
                arrayOf(
                    0f to edgeColor,
                    bandFraction to Color.Transparent,
                    (1f - bandFraction) to Color.Transparent,
                    1f to edgeColor,
                )
            val brush =
                if (isVerticalPull) {
                    Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height)
                } else {
                    Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = size.width)
                }
            drawRect(brush = brush)
        }
    }
}

private const val SCREEN_REORIENT_MAX_BLUR_DP = 12
private const val SCREEN_REORIENT_EDGE_DARKEN_ALPHA = 0.55f

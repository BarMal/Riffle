package com.riffle.app.launcher

import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
 * The dock re-orientation's "iPhone Duo" frost (dock-reorient decisions, follow-up to #1278): a
 * darken scrim over the dock, continuously proportional to [strengthProvider] (0 at rest, 1 at the
 * frost's strongest -- see [com.riffle.core.domain.launcher.dockpull.dockPullReorientFrostStrength]),
 * read inside a draw block so it animates without recomposing, exactly like [dockSurfaceAppearance]'s
 * own pull branch. [strengthProvider] is only called while [isActive] -- pass the dock pull's
 * `transitioning` flag, which changes just at a pull's start and end, not [strengthProvider]'s value.
 *
 * The blur itself is a real one on API 31+, where [RenderEffect][android.graphics.RenderEffect]-backed
 * [Modifier.blur] is available (gated with the codebase's established `Build.VERSION.SDK_INT` check,
 * e.g. [AndroidHomeRoleGateway][com.riffle.app.launcher.AndroidHomeRoleGateway]); below API 31 the
 * darken alone carries the effect -- it never silently no-ops and never crashes. Its radius is fixed
 * at [isActive]'s composition-level max rather than also riding [strengthProvider] frame to frame:
 * [Modifier.blur]'s radius is set once when the modifier chain is built, not read lazily like a draw
 * block, so animating it continuously would mean rebuilding the modifier (and recomposing the dock)
 * every frame of the pull -- the one thing this binding is built to avoid. The darken scrim still
 * ramps continuously; only the blur's own strength is a step rather than a ramp.
 */
internal fun Modifier.dockReorientFrost(
    isActive: Boolean,
    strengthProvider: () -> Float,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Modifier {
    if (!isActive) return this
    val blurred =
        if (sdkInt >= Build.VERSION_CODES.S) {
            blur(radius = DOCK_REORIENT_MAX_BLUR_DP.dp)
        } else {
            this
        }
    return blurred.drawWithContent {
        drawContent()
        val strength = strengthProvider().coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(color = Color.Black.copy(alpha = DOCK_REORIENT_MAX_DARKEN_ALPHA * strength))
        }
    }
}

private const val DOCK_REORIENT_MAX_BLUR_DP = 12
private const val DOCK_REORIENT_MAX_DARKEN_ALPHA = 0.35f

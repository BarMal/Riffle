package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

@Composable
internal fun Modifier.dockSurfaceAppearance(dock: DockModel): Modifier {
    val spec = dockAppearanceSpec(dock.visualEffect, dock.cornerRadiusDp)
    val shape = RoundedCornerShape(spec.cornerRadiusDp.dp)
    var result = this
    if (spec.elevationDp > 0) result = result.shadow(spec.elevationDp.dp, shape)
    result = result.clip(shape).background(dockSurfaceColor(dock))
    if (spec.outlineWidthDp > 0) {
        result = result.border(spec.outlineWidthDp.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    return result
}

/**
 * Draws a fading-in ring around the dock while a home-grid item is being dragged over it, so the
 * dock's own drop zone is visible before the drag is released instead of only afterward.
 */
@Composable
internal fun Modifier.dockDropHighlight(
    dock: DockModel,
    isHighlighted: Boolean,
): Modifier {
    val alpha by
        animateFloatAsState(
            targetValue = if (isHighlighted) 1f else 0f,
            animationSpec = tween(DOCK_DROP_HIGHLIGHT_ANIMATION_MILLIS),
            label = "dockDropHighlightAlpha",
        )
    if (alpha <= 0f) return this
    val shape = RoundedCornerShape(dockAppearanceSpec(dock.visualEffect, dock.cornerRadiusDp).cornerRadiusDp.dp)
    return border(DOCK_DROP_HIGHLIGHT_WIDTH_DP.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
}

private const val DOCK_DROP_HIGHLIGHT_WIDTH_DP = 2
private const val DOCK_DROP_HIGHLIGHT_ANIMATION_MILLIS = 120

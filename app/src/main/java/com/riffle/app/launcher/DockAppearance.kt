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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.DockModel

@Composable
internal fun dockSurfaceColor(dock: DockModel): Color = dockFillColor(dock, MaterialTheme.colorScheme.surfaceVariant)

/**
 * The Dock's fill for its own "Dock background" setting: clear at 0%, the Thin glass fill at the
 * default (72%), the dock's own surface colour fully opaque at 100%, linear between. Reduced
 * transparency is the setting's job here (it lifts the fill to the Thick one).
 */
@Composable
internal fun dockFillColor(
    dock: DockModel,
    surface: Color,
): Color {
    val reduced = LocalLiquidGlassSettings.current.reducedTransparency
    return dockFillColor(dock.backgroundAlphaPercent, surface, reduced)
}

internal fun dockFillColor(
    backgroundAlphaPercent: Int,
    surface: Color,
    reducedTransparency: Boolean = false,
): Color {
    val thin = glassFillColor(GlassWeight.Thin, surface, reducedTransparency)
    val (alpha, surfaceShare) =
        GlassMaterialTokens.dockFill(backgroundAlphaPercent, DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT, thin.alpha)
    val mixed = lerp(thin.copy(alpha = 1f), surface.copy(alpha = 1f), surfaceShare)
    // 0% stays clear even under reduced transparency: it is the owner's explicit choice.
    val clear = backgroundAlphaPercent <= 0
    return mixed.copy(alpha = if (clear) 0f else alpha)
}

/** A dock background at its own alpha: what every dock outside a dock pull draws. */
internal val OpaqueDockBackground: () -> Float = { 1f }

/** The dock is not pressed down: it draws in place. */
internal val NoDockDip: () -> Float = { 0f }

/**
 * The multiplier the dock pull applies to the dock background's alpha (Decision 4), read at draw
 * time so a pull animates the background without recomposing the dock.
 */
internal val LocalDockBackgroundAlpha = staticCompositionLocalOf { OpaqueDockBackground }

@Composable
internal fun Modifier.dockSurfaceAppearance(dock: DockModel): Modifier {
    val shape = RoundedCornerShape(dock.cornerRadiusDp.dp)
    val backgroundAlpha = LocalDockBackgroundAlpha.current
    // The dock's fill alpha is its own "Dock background" setting, exactly: 0% clear, 100% opaque.
    val color = dockSurfaceColor(dock)
    val edgeLights = Modifier.glassEdgeLights(shape, GlassWeight.Thin, color, alphaProvider = backgroundAlpha)
    if (backgroundAlpha === OpaqueDockBackground) {
        return this.clip(shape).background(color).then(edgeLights)
    }
    // Under a dock pull: the same fill and edge lights, faded by the pull at draw time.
    return graphicsLayer {
        this.shape = shape
        clip = true
    }.drawBehind {
        val alpha = backgroundAlpha().coerceIn(0f, 1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = color.copy(alpha = color.alpha * alpha))
    }.then(edgeLights)
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
    val shape = RoundedCornerShape(dock.cornerRadiusDp.dp)
    return border(DOCK_DROP_HIGHLIGHT_WIDTH_DP.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
}

private const val DOCK_DROP_HIGHLIGHT_WIDTH_DP = 2
private const val DOCK_DROP_HIGHLIGHT_ANIMATION_MILLIS = 120

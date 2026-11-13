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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel

@Composable
internal fun dockSurfaceColor(dock: DockModel): Color {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    return baseColor.copy(alpha = baseColor.alpha * dock.backgroundAlphaPercent / 100f)
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
    val shape = RoundedCornerShape(dock.cornerRadiusDp.dp)
    val backgroundAlpha = LocalDockBackgroundAlpha.current
    val sheenStrength = glassSheenStrength(dockSurfaceColor(dock))
    if (backgroundAlpha === OpaqueDockBackground) {
        return this
            .clip(shape)
            .dockAtRestBackground(shape, dock)
            .glassSheen(shape, sheenStrength, backgroundAlpha)
    }
    // Under a dock pull: the same fill, faded by the pull at draw time.
    val color = dockSurfaceColor(dock)
    val sheen = Modifier.glassSheen(shape, sheenStrength, backgroundAlpha)
    return graphicsLayer {
        this.shape = shape
        clip = true
    }.drawBehind {
        val alpha = backgroundAlpha().coerceIn(0f, 1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = color.copy(alpha = color.alpha * alpha))
    }.then(sheen)
}

/**
 * The dock's at-rest background fill (branch 1 of [dockSurfaceAppearance] -- the dock-pull
 * transition in branch 2 is untouched by this and keeps its own flat [dockSurfaceColor] fade,
 * documented as a known follow-up where [dockSurfaceAppearance] calls this).
 *
 * When liquid glass is enabled and this call site is eligible for the real shader treatment (API
 * 33+, a [LocalLiquidGlassBackdrop] installed above Home's root, not under Robolectric -- the exact
 * same gate [GlassSurface] itself checks), the dock's flat [dockSurfaceColor] fill is replaced by
 * [liquidGlassShaderBackground], the same backdrop-refraction material `GlassSurface` gives its own
 * content, using [dockSurfaceColor] as the shader's tint and [shape] as the surface actually clips
 * to (already applied by the caller).
 * Otherwise this is exactly today's flat [dockSurfaceColor] fill -- zero behavior change.
 */
@Composable
private fun Modifier.dockAtRestBackground(
    shape: Shape,
    dock: DockModel,
): Modifier {
    val liquidGlass = LocalLiquidGlassSettings.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val color = dockSurfaceColor(dock)

    // Nested rather than one compound condition, matching GlassSurface.kt's own structure: Android
    // Lint's NewApi check does not trace a precomputed boolean referencing SDK_INT across a function
    // call boundary, so the API-33 shader call must sit behind a *direct*
    // `Build.VERSION.SDK_INT >= ...` check here, not one folded into a single condition with the
    // Robolectric guard (which would also push this over detekt's ComplexCondition threshold).
    if (Build.FINGERPRINT != "robolectric") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && liquidGlass.samplesBackdrop && backdrop != null) {
            return liquidGlassShaderBackground(
                shape = shape,
                tint = color,
                backdrop = backdrop,
                frostStrength = liquidGlass.frostStrengthFraction,
                refractionStrength = liquidGlass.refractionStrengthFraction,
                // Over the wallpaper (which the shader cannot sample) the dock is its own setting,
                // exactly: 0% clear, 100% opaque. Over app content frost and refraction apply.
                wallpaperTintAlpha = color.alpha,
            )
        }
    }
    return background(color)
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

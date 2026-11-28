package com.riffle.app.launcher.summon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.riffle.app.launcher.designsystem.RiffleMotion

internal const val SUMMON_FOCUSED_SCALE = 1.18f
private const val REST_GLOW = 0.18f
private const val FOCUSED_GLOW = 0.5f
private const val GAIN_MILLIS = 180
private const val LOSS_MILLIS = 280
private const val GLOW_REACH = 0.8f
private const val GLOW_MID_STOP = 0.55f
private const val GLOW_MID_ALPHA = 0.3f

/** How big a ring item draws and how strongly it glows, 0..1. */
@Immutable
internal data class SummonFocusLook(
    val scale: Float,
    val glow: Float,
)

internal fun summonFocusLook(
    focused: Boolean,
    reducedMotion: Boolean,
): SummonFocusLook =
    SummonFocusLook(
        scale = if (focused && !reducedMotion) SUMMON_FOCUSED_SCALE else 1f,
        glow = if (focused) FOCUSED_GLOW else REST_GLOW,
    )

/** Gaining focus is quick so the thumb feels the hit; losing it settles a touch slower so it does not flicker. */
internal fun summonFocusMillis(gaining: Boolean): Int = if (gaining) GAIN_MILLIS else LOSS_MILLIS

/** A glow is the icon's own colour; neutral light until that colour has been read from the icon. */
internal fun summonGlowColor(iconColor: Color?): Color = iconColor ?: Color.White

@Composable
internal fun animateSummonFocus(
    focused: Boolean,
    reducedMotion: Boolean,
): SummonFocusLook {
    val target = summonFocusLook(focused, reducedMotion)
    val easing = if (focused) RiffleMotion.EmphasizedDecelerateEasing else RiffleMotion.StandardEasing
    val spec = tween<Float>(summonFocusMillis(gaining = focused), easing = easing)
    val scale by animateFloatAsState(target.scale, spec, label = "summon-focus-scale")
    val glow by animateFloatAsState(target.glow, spec, label = "summon-focus-glow")
    return SummonFocusLook(scale, glow)
}

/** Light cast by the item itself, round it and past its bounds: no disc, just its colour fading out. */
internal fun Modifier.summonGlow(
    color: Color,
    strength: () -> Float,
): Modifier =
    drawBehind {
        val alpha = strength().coerceIn(0f, 1f)
        val radius = size.minDimension * GLOW_REACH
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    GLOW_MID_STOP to color.copy(alpha = alpha * GLOW_MID_ALPHA),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
            radius = radius,
        )
    }

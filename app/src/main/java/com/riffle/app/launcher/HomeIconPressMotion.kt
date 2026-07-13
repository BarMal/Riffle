package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

internal fun homeIconPressMotionPolicy(reducedMotion: Boolean): HomeIconPressMotionPolicy =
    if (reducedMotion) HomeIconPressMotionPolicy.NONE else HomeIconPressMotionPolicy.SHRINK

internal enum class HomeIconPressMotionPolicy(
    val pressedScale: Float,
) {
    NONE(pressedScale = 1f),
    SHRINK(pressedScale = 0.94f),
}

internal fun Modifier.homeIconPressMotion(
    interactionSource: InteractionSource,
    policy: HomeIconPressMotionPolicy,
): Modifier =
    composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by
            animateFloatAsState(
                targetValue = if (isPressed) policy.pressedScale else 1f,
                animationSpec = tween(durationMillis = HOME_ICON_PRESS_DURATION_MILLIS),
                label = "home-icon-press",
            )

        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

internal const val HOME_ICON_PRESS_DURATION_MILLIS = 90

package com.riffle.app.launcher

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength

interface LauncherHaptics {
    fun longPress()

    fun adaptiveStageSettle(strength: AdaptiveStageHapticStrength)
}

object NoopLauncherHaptics : LauncherHaptics {
    override fun longPress() = Unit

    override fun adaptiveStageSettle(strength: AdaptiveStageHapticStrength) = Unit
}

@Composable
fun rememberLauncherHaptics(strength: HapticFeedbackStrength): LauncherHaptics {
    val view = LocalView.current

    return remember(view, strength) {
        ViewLauncherHaptics(
            view = view,
            strength = strength,
        )
    }
}

private class ViewLauncherHaptics(
    private val view: View,
    private val strength: HapticFeedbackStrength,
) : LauncherHaptics {
    override fun longPress() {
        strength.longPressHapticFeedbackConstant()?.let { constant ->
            view.performHapticFeedback(constant)
        }
    }

    override fun adaptiveStageSettle(strength: AdaptiveStageHapticStrength) {
        strength.adaptiveStageSettleHapticFeedbackConstant()?.let { constant ->
            view.performHapticFeedback(constant)
        }
    }
}

internal fun HapticFeedbackStrength.longPressHapticFeedbackConstant(): Int? =
    when (this) {
        HapticFeedbackStrength.OFF -> null
        HapticFeedbackStrength.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        HapticFeedbackStrength.MEDIUM -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticFeedbackStrength.STRONG -> HapticFeedbackConstants.LONG_PRESS
    }

internal fun AdaptiveStageHapticStrength.adaptiveStageSettleHapticFeedbackConstant(): Int? =
    when (this) {
        AdaptiveStageHapticStrength.OFF -> null
        AdaptiveStageHapticStrength.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        AdaptiveStageHapticStrength.MEDIUM -> HapticFeedbackConstants.CONTEXT_CLICK
        AdaptiveStageHapticStrength.STRONG -> HapticFeedbackConstants.LONG_PRESS
    }

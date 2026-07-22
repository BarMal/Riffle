package com.riffle.app.launcher

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.TimeScapeHapticStrength

interface LauncherHaptics {
    fun longPress()

    fun timeScapeSettle(strength: TimeScapeHapticStrength)
}

object NoopLauncherHaptics : LauncherHaptics {
    override fun longPress() = Unit

    override fun timeScapeSettle(strength: TimeScapeHapticStrength) = Unit
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

    override fun timeScapeSettle(strength: TimeScapeHapticStrength) {
        strength.timeScapeSettleHapticFeedbackConstant()?.let { constant ->
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

internal fun TimeScapeHapticStrength.timeScapeSettleHapticFeedbackConstant(): Int? =
    when (this) {
        TimeScapeHapticStrength.OFF -> null
        TimeScapeHapticStrength.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        TimeScapeHapticStrength.MEDIUM -> HapticFeedbackConstants.CONTEXT_CLICK
        TimeScapeHapticStrength.STRONG -> HapticFeedbackConstants.LONG_PRESS
    }

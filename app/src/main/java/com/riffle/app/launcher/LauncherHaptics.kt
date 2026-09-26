package com.riffle.app.launcher

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength

interface LauncherHaptics {
    fun longPress()

    fun adaptiveStageSettle(strength: AdaptiveStageHapticStrength)

    /** Plays one semantic [event] from the launcher's haptic vocabulary. */
    fun perform(event: LauncherHapticEvent) = Unit
}

/**
 * The launcher's semantic haptic vocabulary. Surfaces name what happened, not which vibration to play;
 * [hapticFeedbackConstant] maps each event to the platform's modern constants with older-API fallbacks.
 */
enum class LauncherHapticEvent {
    /** A drag or gesture has been recognised and is now tracking the finger. */
    GESTURE_START,

    /** A tracked gesture was released. */
    GESTURE_END,

    /** Passing a discrete position, such as the stack settling on the next card. */
    DETENT,

    /** Reaching the end of a range, such as the first or last card of a stack. */
    BOUNDARY,

    /** An action was accepted, such as a pill or a dock drop committing. */
    COMMIT,

    /** An action was rejected or abandoned, such as a drop that cannot land. */
    CANCEL,
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

    override fun perform(event: LauncherHapticEvent) {
        if (strength == HapticFeedbackStrength.OFF) return
        view.performHapticFeedback(event.hapticFeedbackConstant())
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

/**
 * Maps a semantic [LauncherHapticEvent] to a [HapticFeedbackConstants] value for [sdkInt]. The API 30
 * (`GESTURE_*`, `CONFIRM`, `REJECT`) and API 34 (`SEGMENT_*`) constants are only returned where the
 * platform defines them; older releases get the closest constant available since API 28.
 */
@SuppressLint("InlinedApi")
internal fun LauncherHapticEvent.hapticFeedbackConstant(sdkInt: Int = Build.VERSION.SDK_INT): Int {
    val hasGestureConstants = sdkInt >= Build.VERSION_CODES.R
    val hasSegmentConstants = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    return when (this) {
        LauncherHapticEvent.GESTURE_START ->
            if (hasGestureConstants) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.VIRTUAL_KEY
        LauncherHapticEvent.GESTURE_END ->
            if (hasGestureConstants) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            }
        LauncherHapticEvent.DETENT ->
            if (hasSegmentConstants) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        LauncherHapticEvent.BOUNDARY ->
            if (hasSegmentConstants) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CONTEXT_CLICK
        LauncherHapticEvent.COMMIT ->
            if (hasGestureConstants) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        LauncherHapticEvent.CANCEL ->
            if (hasGestureConstants) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
}

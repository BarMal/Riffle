package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

/**
 * A settings control that exposes a bounded integer range as discrete slider stops.
 *
 * Ticks a haptic each time a drag crosses one of those stops (see [tactileSliderHapticTick]) --
 * a plain detent tick mid-range, a distinct boundary tick at the first or last stop -- so the
 * control feels like a dial with real steps rather than a smooth, silent ramp.
 */
@Composable
internal fun DiscreteSettingSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueLabel: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val persistedValue = value.coerceIn(valueRange)
    var previewValue by
        remember(value, valueRange) {
            mutableFloatStateOf(persistedValue.toFloat())
        }
    val selectedValue = previewValue.roundToInt().coerceIn(valueRange)
    val formattedValue = valueLabel(selectedValue)
    val view = LocalView.current
    var lastTickedValue by remember(value, valueRange) { mutableIntStateOf(persistedValue) }

    fun tickHapticIfCrossed(next: Int) {
        if (next == lastTickedValue) return
        val constant =
            when (tactileSliderHapticTick(lastTickedValue, next, valueRange)) {
                TactileSliderHapticTick.DETENT -> LauncherHapticEvent.DETENT.hapticFeedbackConstant()
                TactileSliderHapticTick.BOUNDARY -> LauncherHapticEvent.BOUNDARY.hapticFeedbackConstant()
                TactileSliderHapticTick.NONE -> null
            }
        constant?.let(view::performHapticFeedback)
        lastTickedValue = next
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsTextColumn(
            title = title,
            subtitle = formattedValue,
        )
        TactileSlider(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title
                        stateDescription = formattedValue
                        setProgress { targetValue ->
                            val selectedAccessibleValue = targetValue.roundToInt().coerceIn(valueRange)
                            previewValue = selectedAccessibleValue.toFloat()
                            tickHapticIfCrossed(selectedAccessibleValue)
                            if (selectedAccessibleValue != persistedValue) {
                                onValueChange(selectedAccessibleValue)
                            }
                            true
                        }
                    },
            value = previewValue,
            onValueChange = { selectedValue ->
                previewValue = selectedValue
                tickHapticIfCrossed(selectedValue.roundToInt().coerceIn(valueRange))
            },
            onValueChangeFinished = {
                val finalValue = previewValue.roundToInt().coerceIn(valueRange)
                if (finalValue != persistedValue) {
                    onValueChange(finalValue)
                }
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        )
    }
}

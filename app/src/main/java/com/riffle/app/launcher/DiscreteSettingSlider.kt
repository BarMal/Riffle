package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

/** A settings control that exposes a bounded integer range as discrete slider stops. */
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

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsTextColumn(
            title = title,
            subtitle = formattedValue,
        )
        Slider(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title
                        stateDescription = formattedValue
                        setProgress { targetValue ->
                            val selectedAccessibleValue = targetValue.roundToInt().coerceIn(valueRange)
                            previewValue = selectedAccessibleValue.toFloat()
                            if (selectedAccessibleValue != persistedValue) {
                                onValueChange(selectedAccessibleValue)
                            }
                            true
                        }
                    },
            value = previewValue,
            onValueChange = { selectedValue -> previewValue = selectedValue },
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

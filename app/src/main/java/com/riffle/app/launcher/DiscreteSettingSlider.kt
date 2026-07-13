package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsTextColumn(
            title = title,
            subtitle = valueLabel(value),
        )
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = value.coerceIn(valueRange).toFloat(),
            onValueChange = { selectedValue -> onValueChange(selectedValue.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        )
    }
}

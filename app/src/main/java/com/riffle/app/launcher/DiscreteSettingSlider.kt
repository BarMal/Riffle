package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

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
    val formattedValue = valueLabel(value)

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
                    },
            value = value.coerceIn(valueRange).toFloat(),
            onValueChange = { selectedValue -> onValueChange(selectedValue.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        )
    }
}

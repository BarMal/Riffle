package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * A [DiscreteSettingSlider] over a value that moves in whole steps ([values], such as `100..400 step 10`): the slider
 * has one stop per step, so it does not offer hundreds of stops, and a stored value between two steps shows the
 * nearest.
 */
@Composable
internal fun SettingsSteppedSlider(
    title: String,
    value: Int,
    values: IntProgression,
    valueLabel: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val min = values.first
    val step = values.step
    val stops = (values.last - min) / step
    DiscreteSettingSlider(
        title = title,
        value = ((value - min) / step.toFloat()).roundToInt().coerceIn(0, stops),
        valueRange = 0..stops,
        valueLabel = { index -> valueLabel(min + index * step) },
        onValueChange = { index -> onValueChange(min + index * step) },
        modifier = modifier,
    )
}

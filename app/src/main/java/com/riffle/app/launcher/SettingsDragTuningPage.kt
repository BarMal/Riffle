package com.riffle.app.launcher

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.home.HomeDragTuning
import kotlin.math.roundToInt

internal const val DRAG_TUNING_VALUES_TEST_TAG = "drag-tuning-values"
internal const val DRAG_TUNING_RESET_TEST_TAG = "drag-tuning-reset"

private const val PERCENT = 100f

/** Settings > About > Developer options > Drag tuning: sliders for the numbers a Home drag reads at runtime, and Reset. */
@Composable
internal fun SettingsDragTuningPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val tuning = state.settings.home.dragTuning

    fun set(next: HomeDragTuning) = onAction(LauncherShellAction.SetDragTuning(next))

    SettingsSection(title = stringResource(R.string.settings_page_drag_tuning)) {
        DiscreteSettingSlider(
            title = stringResource(R.string.drag_tuning_ring),
            value = (tuning.ringRadius * PERCENT).roundToInt(),
            valueRange = percentRange(HomeDragTuning.RING_RANGE),
            valueLabel = { "%.2f".format(it / PERCENT) },
            onValueChange = { set(tuning.copy(ringRadius = it / PERCENT)) },
        )
        DiscreteSettingSlider(
            title = stringResource(R.string.drag_tuning_dead_zone),
            value = (tuning.deadZoneRadius * PERCENT).roundToInt(),
            valueRange = percentRange(HomeDragTuning.DEAD_ZONE_RANGE),
            valueLabel = { "%.2f".format(it / PERCENT) },
            onValueChange = { set(tuning.copy(deadZoneRadius = it / PERCENT)) },
        )
        DiscreteSettingSlider(
            title = stringResource(R.string.drag_tuning_folder_hold),
            value = tuning.folderHoldMillis,
            valueRange = HomeDragTuning.FOLDER_HOLD_RANGE,
            valueLabel = { "$it ms" },
            onValueChange = { set(tuning.copy(folderHoldMillis = it)) },
        )
        DiscreteSettingSlider(
            title = stringResource(R.string.drag_tuning_edge_hold),
            value = tuning.edgeHoldMillis,
            valueRange = HomeDragTuning.EDGE_HOLD_RANGE,
            valueLabel = { "$it ms" },
            onValueChange = { set(tuning.copy(edgeHoldMillis = it)) },
        )
        DiscreteSettingSlider(
            title = stringResource(R.string.drag_tuning_page_repeat),
            value = tuning.pageRepeatMillis,
            valueRange = HomeDragTuning.PAGE_REPEAT_RANGE,
            valueLabel = { "$it ms" },
            onValueChange = { set(tuning.copy(pageRepeatMillis = it)) },
        )
        TextButton(
            modifier = Modifier.testTag(DRAG_TUNING_RESET_TEST_TAG),
            onClick = { set(HomeDragTuning()) },
        ) {
            Text(stringResource(R.string.drag_tuning_reset))
        }
        SettingsPrimaryText(text = stringResource(R.string.drag_tuning_values_title))
        SelectionContainer {
            Text(modifier = Modifier.testTag(DRAG_TUNING_VALUES_TEST_TAG), text = tuning.asText())
        }
    }
}

private fun percentRange(range: ClosedFloatingPointRange<Float>): IntRange =
    (range.start * PERCENT).roundToInt()..(range.endInclusive * PERCENT).roundToInt()

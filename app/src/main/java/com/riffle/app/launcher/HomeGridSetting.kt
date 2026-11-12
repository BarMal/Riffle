package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.MAX_HOME_GRID_MARGIN_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_GRID_MARGIN_DP

/**
 * [visibleDimensions] is the grid actually on screen right now -- [GridSettings.dimensions]
 * itself is the pre-dock number underneath it, a storage detail this control never shows. A side
 * dock silently reserves one of the columns [GridSettings.dimensions] counts, so editing that raw
 * number directly is how "+1 column" used to end up cancelling the dock's own reservation instead
 * of adding a column to what's on screen. Dispatching edits off [visibleDimensions] instead means
 * "+1" always means one more of what the user is looking at, dock or no dock.
 */
@Composable
internal fun HomeGridSetting(
    grid: GridSettings,
    visibleDimensions: GridDimensions,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsStepperRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_GRID_COLUMNS).testTag(HOME_GRID_COLUMNS_TEST_TAG),
            title = SettingKey.HOME_GRID_COLUMNS.label(),
            value = visibleDimensions.columns,
            valueRange = MIN_GRID_DIMENSION..maxOf(MAX_GRID_DIMENSION, visibleDimensions.columns),
            onValueChange = { columns ->
                onAction(LauncherShellAction.SelectHomeGridDimensions(visibleDimensions.copy(columns = columns)))
            },
        )
        SettingsStepperRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_GRID_ROWS).testTag(HOME_GRID_ROWS_TEST_TAG),
            title = SettingKey.HOME_GRID_ROWS.label(),
            value = visibleDimensions.rows,
            valueRange = MIN_GRID_DIMENSION..maxOf(MAX_GRID_DIMENSION, visibleDimensions.rows),
            onValueChange = { rows ->
                onAction(LauncherShellAction.SelectHomeGridDimensions(visibleDimensions.copy(rows = rows)))
            },
        )
        DiscreteSettingSlider(
            modifier = Modifier.settingAnchor(SettingKey.HOME_GRID_MARGIN_HORIZONTAL),
            title = SettingKey.HOME_GRID_MARGIN_HORIZONTAL.label(),
            value = grid.margin.start,
            valueRange = MIN_HOME_GRID_MARGIN_DP..MAX_HOME_GRID_MARGIN_DP,
            valueLabel = { "$it dp" },
            onValueChange = { horizontalDp ->
                onAction(
                    LauncherShellAction.SelectHomeGridMargin(horizontalDp = horizontalDp, verticalDp = grid.margin.top),
                )
            },
        )
        DiscreteSettingSlider(
            modifier = Modifier.settingAnchor(SettingKey.HOME_GRID_MARGIN_VERTICAL),
            title = SettingKey.HOME_GRID_MARGIN_VERTICAL.label(),
            value = grid.margin.top,
            valueRange = MIN_HOME_GRID_MARGIN_DP..MAX_HOME_GRID_MARGIN_DP,
            valueLabel = { "$it dp" },
            onValueChange = { verticalDp ->
                onAction(
                    LauncherShellAction.SelectHomeGridMargin(horizontalDp = grid.margin.start, verticalDp = verticalDp),
                )
            },
        )
    }
}

internal const val HOME_GRID_COLUMNS_TEST_TAG = "home-grid-columns"
internal const val HOME_GRID_ROWS_TEST_TAG = "home-grid-rows"

private const val MIN_GRID_DIMENSION = 1

/** The reducer clamps to what the page can hold; this only keeps "increase" from running off the end. */
private const val MAX_GRID_DIMENSION = 12

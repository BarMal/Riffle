package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.LauncherViewMode
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
    viewMode: LauncherViewMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GridDimensionSetting(
            label = "Columns",
            value = visibleDimensions.columns,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        visibleDimensions.copy(columns = visibleDimensions.columns - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        visibleDimensions.copy(columns = visibleDimensions.columns + 1),
                    ),
                )
            },
        )
        GridDimensionSetting(
            label = "Rows",
            value = visibleDimensions.rows,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        visibleDimensions.copy(rows = visibleDimensions.rows - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        visibleDimensions.copy(rows = visibleDimensions.rows + 1),
                    ),
                )
            },
        )
        GridMarginSetting(
            label = "Horizontal screen margin",
            value = grid.margin.start,
            onValueChange = { horizontalDp ->
                onAction(
                    LauncherShellAction.SelectHomeGridMargin(
                        horizontalDp = horizontalDp,
                        verticalDp = grid.margin.top,
                    ),
                )
            },
        )
        GridMarginSetting(
            label = "Vertical screen margin",
            value = grid.margin.top,
            onValueChange = { verticalDp ->
                onAction(
                    LauncherShellAction.SelectHomeGridMargin(
                        horizontalDp = grid.margin.start,
                        verticalDp = verticalDp,
                    ),
                )
            },
        )
        if (viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY) {
            LibraryPageCompactionSetting(
                enabled = grid.compactLibraryPages,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun GridMarginSetting(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) = DiscreteSettingSlider(
    title = label,
    value = value,
    valueRange = MIN_HOME_GRID_MARGIN_DP..MAX_HOME_GRID_MARGIN_DP,
    valueLabel = { "$it dp" },
    onValueChange = onValueChange,
)

@Composable
private fun GridDimensionSetting(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = label,
            subtitle = value.toString(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = value > MIN_GRID_DIMENSION,
                onClick = onDecrease,
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(onClick = onIncrease) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

@Composable
private fun LibraryPageCompactionSetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Compact library pages",
        subtitle =
            if (enabled) {
                "Reflow generated apps to fill earlier pages"
            } else {
                "Keep incomplete pages when the grid changes"
            },
        checked = enabled,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectLibraryPageCompaction(value)) },
    )
}

private const val MIN_GRID_DIMENSION = 1

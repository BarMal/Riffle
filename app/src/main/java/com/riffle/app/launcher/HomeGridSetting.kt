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
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.MAX_HOME_GRID_MARGIN_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_GRID_MARGIN_DP

@Composable
internal fun HomeGridSetting(
    grid: GridSettings,
    viewMode: LauncherViewMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GridDimensionSetting(
            label = "Columns",
            value = grid.dimensions.columns,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        grid.dimensions.copy(columns = grid.dimensions.columns - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        grid.dimensions.copy(columns = grid.dimensions.columns + 1),
                    ),
                )
            },
        )
        GridDimensionSetting(
            label = "Rows",
            value = grid.dimensions.rows,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        grid.dimensions.copy(rows = grid.dimensions.rows - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        grid.dimensions.copy(rows = grid.dimensions.rows + 1),
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = label,
            subtitle = "$value dp",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = value > MIN_HOME_GRID_MARGIN_DP,
                onClick = { onValueChange(value - 1) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = value < MAX_HOME_GRID_MARGIN_DP,
                onClick = { onValueChange(value + 1) },
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

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

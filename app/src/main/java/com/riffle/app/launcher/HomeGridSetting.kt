package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.LauncherViewMode

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
        if (viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY) {
            LibraryPageCompactionSetting(
                enabled = grid.compactLibraryPages,
                onAction = onAction,
            )
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = value > MIN_GRID_DIMENSION,
                onClick = onDecrease,
            ) {
                Text(text = "-")
            }
            TextButton(onClick = onIncrease) {
                Text(text = "+")
            }
        }
    }
}

@Composable
private fun LibraryPageCompactionSetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Compact Library pages",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (enabled) "Fill earlier pages first" else "Allow incomplete pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { value -> onAction(LauncherShellAction.SelectLibraryPageCompaction(value)) },
        )
    }
}

private const val MIN_GRID_DIMENSION = 1

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.GridDimensions

@Composable
internal fun HomeGridSetting(
    dimensions: GridDimensions,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GridDimensionSetting(
            label = "Columns",
            value = dimensions.columns,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        dimensions.copy(columns = dimensions.columns - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        dimensions.copy(columns = dimensions.columns + 1),
                    ),
                )
            },
        )
        GridDimensionSetting(
            label = "Rows",
            value = dimensions.rows,
            onDecrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        dimensions.copy(rows = dimensions.rows - 1),
                    ),
                )
            },
            onIncrease = {
                onAction(
                    LauncherShellAction.SelectHomeGridDimensions(
                        dimensions.copy(rows = dimensions.rows + 1),
                    ),
                )
            },
        )
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

private const val MIN_GRID_DIMENSION = 1

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
import com.riffle.core.domain.launcher.home.DockModel

@Composable
internal fun DockSetting(
    dock: DockModel,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DockVisibilitySetting(
            enabled = dock.isEnabled,
            onAction = onAction,
        )
        DockCapacitySetting(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            onAction = onAction,
        )
    }
}

@Composable
private fun DockVisibilitySetting(
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
                text = "Show dock",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (enabled) "Dock visible on home" else "Home grid uses dock space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockEnabled(value)) },
        )
    }
}

@Composable
private fun DockCapacitySetting(
    capacity: Int,
    itemCount: Int,
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
                text = "Dock slots",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = capacity.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = capacity > itemCount,
                onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity - 1)) },
            ) {
                Text(text = "-")
            }
            TextButton(onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity + 1)) }) {
                Text(text = "+")
            }
        }
    }
}

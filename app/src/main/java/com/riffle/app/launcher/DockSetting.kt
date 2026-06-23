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
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP

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
        DockIconSizeSetting(
            sizeDp = dock.iconSizeDp,
            onAction = onAction,
        )
        DockBackgroundAlphaSetting(
            alphaPercent = dock.backgroundAlphaPercent,
            onAction = onAction,
        )
    }
}

@Composable
private fun DockIconSizeSetting(
    sizeDp: Int,
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
                text = "Dock icon size",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "$sizeDp dp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizeDp > MIN_DOCK_ICON_SIZE_DP,
                onClick = { onAction(LauncherShellAction.SelectDockIconSize(sizeDp - DOCK_ICON_SIZE_STEP_DP)) },
            ) {
                Text(text = "-")
            }
            TextButton(
                enabled = sizeDp < MAX_DOCK_ICON_SIZE_DP,
                onClick = { onAction(LauncherShellAction.SelectDockIconSize(sizeDp + DOCK_ICON_SIZE_STEP_DP)) },
            ) {
                Text(text = "+")
            }
        }
    }
}

private const val DOCK_ICON_SIZE_STEP_DP = 4

@Composable
private fun DockBackgroundAlphaSetting(
    alphaPercent: Int,
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
                text = "Dock background",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "$alphaPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = alphaPercent > MIN_DOCK_BACKGROUND_ALPHA_PERCENT,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectDockBackgroundAlpha(
                            alphaPercent - DOCK_BACKGROUND_ALPHA_STEP_PERCENT,
                        ),
                    )
                },
            ) {
                Text(text = "-")
            }
            TextButton(
                enabled = alphaPercent < MAX_DOCK_BACKGROUND_ALPHA_PERCENT,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectDockBackgroundAlpha(
                            alphaPercent + DOCK_BACKGROUND_ALPHA_STEP_PERCENT,
                        ),
                    )
                },
            ) {
                Text(text = "+")
            }
        }
    }
}

private const val DOCK_BACKGROUND_ALPHA_STEP_PERCENT = 5

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

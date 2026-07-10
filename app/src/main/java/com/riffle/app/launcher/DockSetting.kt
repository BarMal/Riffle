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
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP

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
        DockNotificationCardsSetting(
            enabled = dock.showNotificationCards,
            onAction = onAction,
        )
        DockCapacitySetting(
            capacity = dock.capacity,
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
        DockBackgroundSizingSetting(
            sizing = dock.backgroundSizing,
            onAction = onAction,
        )
        DockItemSpacingSetting(
            spacingDp = dock.itemSpacingDp,
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
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock icon size",
            subtitle = "$sizeDp dp",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizeDp > MIN_DOCK_ICON_SIZE_DP,
                onClick = { onAction(LauncherShellAction.SelectDockIconSize(sizeDp - DOCK_ICON_SIZE_STEP_DP)) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = sizeDp < MAX_DOCK_ICON_SIZE_DP,
                onClick = { onAction(LauncherShellAction.SelectDockIconSize(sizeDp + DOCK_ICON_SIZE_STEP_DP)) },
            ) {
                SettingsButtonText(text = "+")
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
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock background",
            subtitle = "$alphaPercent%",
        )
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
                SettingsButtonText(text = "-")
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
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val DOCK_BACKGROUND_ALPHA_STEP_PERCENT = 5

@Composable
private fun DockBackgroundSizingSetting(
    sizing: DockBackgroundSizing,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock background size",
            subtitle =
                when (sizing) {
                    DockBackgroundSizing.DYNAMIC -> "Dynamic"
                    DockBackgroundSizing.FIXED -> "Fixed"
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizing != DockBackgroundSizing.DYNAMIC,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.DYNAMIC)) },
            ) {
                SettingsButtonText(text = "Dynamic")
            }
            TextButton(
                enabled = sizing != DockBackgroundSizing.FIXED,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED)) },
            ) {
                SettingsButtonText(text = "Fixed")
            }
        }
    }
}

@Composable
private fun DockItemSpacingSetting(
    spacingDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock item spacing",
            subtitle = "$spacingDp dp",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = spacingDp > MIN_DOCK_ITEM_SPACING_DP,
                onClick = { onAction(LauncherShellAction.SelectDockItemSpacing(spacingDp - DOCK_SPACING_STEP_DP)) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = spacingDp < MAX_DOCK_ITEM_SPACING_DP,
                onClick = { onAction(LauncherShellAction.SelectDockItemSpacing(spacingDp + DOCK_SPACING_STEP_DP)) },
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val DOCK_SPACING_STEP_DP = 2

@Composable
private fun DockVisibilitySetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Show dock",
        subtitle = if (enabled) "Dock visible on home" else "Home grid uses dock space",
        checked = enabled,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockEnabled(value)) },
    )
}

@Composable
private fun DockCapacitySetting(
    capacity: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock slots",
            subtitle = capacity.toString(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = capacity > 0,
                onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity - 1)) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity + 1)) }) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

@Composable
private fun DockNotificationCardsSetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Expanded dock cards",
        subtitle =
            if (enabled) {
                "Expanded dock can show notification cards"
            } else {
                "Expanded dock only shows shortcuts and widgets"
            },
        checked = enabled,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockNotificationCardsEnabled(value)) },
    )
}

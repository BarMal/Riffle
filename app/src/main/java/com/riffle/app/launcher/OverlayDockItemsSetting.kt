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
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection

@Composable
internal fun OverlayDockItemsSetting(
    items: List<AppShortcutItem>,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(
            title = "Floating dock apps",
            subtitle = items.floatingDockItemCountLabel(),
        )
        items.forEachIndexed { index, item ->
            OverlayDockItemSetting(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < items.lastIndex,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun OverlayDockItemSetting(
    item: AppShortcutItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = item.label,
            subtitle = item.appIdentity.packageName.value,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingDockMoveButton(
                label = "Up",
                enabled = canMoveUp,
                item = item,
                direction = OverlayDockItemMoveDirection.UP,
                onAction = onAction,
            )
            FloatingDockMoveButton(
                label = "Down",
                enabled = canMoveDown,
                item = item,
                direction = OverlayDockItemMoveDirection.DOWN,
                onAction = onAction,
            )
            TextButton(
                onClick = { onAction(LauncherShellAction.RemoveFloatingDockShortcut(item.id)) },
            ) {
                SettingsButtonText(text = "Remove")
            }
        }
    }
}

@Composable
private fun FloatingDockMoveButton(
    label: String,
    enabled: Boolean,
    item: AppShortcutItem,
    direction: OverlayDockItemMoveDirection,
    onAction: (LauncherShellAction) -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = {
            onAction(
                LauncherShellAction.MoveFloatingDockShortcut(
                    itemId = item.id,
                    direction = direction,
                ),
            )
        },
    ) {
        SettingsButtonText(text = label)
    }
}

private fun List<AppShortcutItem>.floatingDockItemCountLabel(): String =
    when (size) {
        0 -> "No apps configured"
        1 -> "1 app"
        else -> "$size apps"
    }

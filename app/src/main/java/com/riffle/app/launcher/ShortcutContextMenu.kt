package com.riffle.app.launcher

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.home.AppShortcutItem

internal enum class ShortcutContextSurface {
    HOME,
    DOCK,
}

internal data class ShortcutContextMenuItem(
    val label: String,
    val action: LauncherShellAction,
)

internal fun shortcutContextMenuItems(
    shortcut: AppShortcutItem,
    surface: ShortcutContextSurface,
): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = "Edit home",
            action = LauncherShellAction.EnterHomeEditMode,
        ),
        ShortcutContextMenuItem(
            label = "App info",
            action = shortcut.openAppInfoAction(),
        ),
        ShortcutContextMenuItem(
            label = "Hide app",
            action = LauncherShellAction.HideApp(shortcut.appIdentity),
        ),
        ShortcutContextMenuItem(
            label = "Uninstall",
            action = LauncherShellAction.UninstallApp(shortcut.appIdentity),
        ),
        ShortcutContextMenuItem(
            label = surface.removeLabel,
            action = surface.removeAction(shortcut),
        ),
    )

@Composable
internal fun ShortcutContextMenu(
    expanded: Boolean,
    items: List<ShortcutContextMenuItem>,
    onDismissRequest: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(text = item.label) },
                onClick = {
                    onDismissRequest()
                    onAction(item.action)
                },
            )
        }
    }
}

private val ShortcutContextSurface.removeLabel: String
    get() =
        when (this) {
            ShortcutContextSurface.HOME -> "Remove from home"
            ShortcutContextSurface.DOCK -> "Remove from dock"
        }

private fun ShortcutContextSurface.removeAction(shortcut: AppShortcutItem): LauncherShellAction =
    when (this) {
        ShortcutContextSurface.HOME -> LauncherShellAction.RemoveHomeShortcut(shortcut.id)
        ShortcutContextSurface.DOCK -> LauncherShellAction.RemoveDockShortcut(shortcut.id)
    }

package com.riffle.app.launcher

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem

internal enum class ShortcutContextSurface {
    HOME,
    DOCK,
}

internal data class ShortcutContextMenuItem(
    val label: String,
    val action: LauncherShellAction,
    val enabled: Boolean = true,
)

internal fun shortcutContextMenuItems(
    shortcut: AppShortcutItem,
    surface: ShortcutContextSurface,
    appShortcuts: List<AppShortcut> = emptyList(),
): List<ShortcutContextMenuItem> {
    val platformShortcutItems =
        appShortcuts.map { appShortcut ->
            ShortcutContextMenuItem(
                label = appShortcut.contextMenuLabel,
                action = LauncherShellAction.LaunchAppShortcut(appShortcut),
                enabled = appShortcut.enabled,
            )
        }
    val managementItems =
        listOf(
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
        ) +
            surface.dockManagementItems(shortcut) +
            ShortcutContextMenuItem(
                label = surface.removeLabel,
                action = surface.removeAction(shortcut),
            )

    return platformShortcutItems + managementItems
}

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
                enabled = item.enabled,
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

private fun ShortcutContextSurface.dockManagementItems(shortcut: AppShortcutItem): List<ShortcutContextMenuItem> =
    when (this) {
        ShortcutContextSurface.HOME ->
            listOf(
                ShortcutContextMenuItem(
                    label = "Add to dock",
                    action =
                        LauncherShellAction.AddAppToDock(
                            InstalledApp(
                                identity = shortcut.appIdentity,
                                label = shortcut.label,
                            ),
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Add to floating dock",
                    action =
                        LauncherShellAction.AddAppToFloatingDock(
                            InstalledApp(
                                identity = shortcut.appIdentity,
                                label = shortcut.label,
                            ),
                        ),
                ),
            )

        ShortcutContextSurface.DOCK -> emptyList()
    }

private val AppShortcut.contextMenuLabel: String
    get() = longLabel ?: shortLabel

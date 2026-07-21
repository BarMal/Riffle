package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem

internal enum class ShortcutContextSurface {
    HOME,
    DOCK,
}

internal data class ShortcutContextMenuItem(
    val label: String,
    val action: LauncherShellAction? = null,
    val enabled: Boolean = true,
    val submenuItems: List<ShortcutContextMenuItem> = emptyList(),
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

    val appShortcutMenu =
        platformShortcutItems.takeIf(List<ShortcutContextMenuItem>::isNotEmpty)?.let { shortcutItems ->
            ShortcutContextMenuItem(
                label = "App shortcuts (${shortcutItems.size})",
                submenuItems = shortcutItems,
            )
        }

    return listOfNotNull(appShortcutMenu) + managementItems
}

@Composable
internal fun ShortcutContextMenu(
    expanded: Boolean,
    items: List<ShortcutContextMenuItem>,
    onDismissRequest: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    offset: DpOffset = DpOffset.Zero,
) {
    var submenuItems by remember(items) { mutableStateOf<List<ShortcutContextMenuItem>?>(null) }
    val visibleItems = submenuItems ?: items

    RiffleContextMenu(
        expanded = expanded,
        onDismissRequest = {
            submenuItems = null
            onDismissRequest()
        },
        offset = offset,
    ) {
        if (submenuItems != null) {
            DropdownMenuItem(
                text = { Text(text = "Back") },
                onClick = { submenuItems = null },
            )
        }
        visibleItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(text = item.label) },
                enabled = item.enabled,
                trailingIcon =
                    item.submenuItems.takeIf(List<ShortcutContextMenuItem>::isNotEmpty)?.let {
                        { Text(text = "›") }
                    },
                onClick = {
                    if (item.submenuItems.isNotEmpty()) {
                        submenuItems = item.submenuItems
                    } else {
                        onDismissRequest()
                        onAction(requireNotNull(item.action))
                    }
                },
            )
        }
    }
}

@Composable
internal fun RiffleContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
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
                    label = "Move to dock",
                    action = LauncherShellAction.MoveHomeItemToDock(shortcut.id),
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

        ShortcutContextSurface.DOCK ->
            listOf(
                ShortcutContextMenuItem(
                    label = "Move to home",
                    action = LauncherShellAction.MoveDockItemToHome(shortcut.id),
                ),
            )
    }

private val AppShortcut.contextMenuLabel: String
    get() = longLabel ?: shortLabel

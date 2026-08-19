package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.WidgetItem

internal enum class ShortcutContextSurface {
    HOME,
    DOCK,

    /**
     * The dock's panel. Its own surface because the panel is not one of the layout's pages, so the
     * home removal -- which acts on the selected page -- never matches an item on it.
     */
    DOCK_PANEL,
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
    val dismissMenu = {
        submenuItems = null
        onDismissRequest()
    }

    RiffleContextMenu(
        expanded = expanded,
        onDismissRequest = dismissMenu,
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
                        dismissMenu()
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
        modifier = modifier.testTag(RIFFLE_CONTEXT_MENU_TEST_TAG),
        offset = offset,
        shape = LocalLauncherPanelShape.current,
        containerColor = launcherMenuSurfaceColor(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = {
            CompositionLocalProvider(LocalContentColor provides launcherMenuContentColor()) {
                content()
            }
        },
    )
}

internal const val RIFFLE_CONTEXT_MENU_TEST_TAG = "riffle-context-menu"

private val ShortcutContextSurface.removeLabel: String
    get() =
        when (this) {
            ShortcutContextSurface.HOME -> "Remove from home"
            ShortcutContextSurface.DOCK -> "Remove from dock"
            ShortcutContextSurface.DOCK_PANEL -> "Remove from panel"
        }

private fun ShortcutContextSurface.removeAction(shortcut: AppShortcutItem): LauncherShellAction =
    when (this) {
        ShortcutContextSurface.HOME -> LauncherShellAction.RemoveHomeShortcut(shortcut.id)
        ShortcutContextSurface.DOCK -> LauncherShellAction.RemoveDockShortcut(shortcut.id)
        ShortcutContextSurface.DOCK_PANEL -> LauncherShellAction.RemoveDockPanelItem(shortcut.id)
    }

/**
 * Where a widget goes when removed depends on what is hosting its grid. The dock's panel is not one
 * of the layout's pages, so the home removal never matches an item on it.
 */
internal fun WidgetItem.removeActionFor(surface: ShortcutContextSurface): LauncherShellAction =
    when (surface) {
        ShortcutContextSurface.DOCK_PANEL -> LauncherShellAction.RemoveDockPanelItem(id)
        ShortcutContextSurface.HOME, ShortcutContextSurface.DOCK -> LauncherShellAction.RemoveHomeShortcut(id)
    }

internal val ShortcutContextSurface.widgetRemoveLabel: String
    get() =
        when (this) {
            ShortcutContextSurface.DOCK_PANEL -> "Remove from panel"
            ShortcutContextSurface.HOME, ShortcutContextSurface.DOCK -> "Remove from home"
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

        ShortcutContextSurface.DOCK ->
            listOf(
                ShortcutContextMenuItem(
                    label = "Move to home",
                    action = LauncherShellAction.MoveDockItemToHome(shortcut.id),
                ),
            )

        // No "move to" offer yet: moving between the panel and a home page means re-placing on a
        // different grid, which the panel's own drag support has to land first.
        ShortcutContextSurface.DOCK_PANEL -> emptyList()
    }

private val AppShortcut.contextMenuLabel: String
    get() = longLabel ?: shortLabel

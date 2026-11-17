package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun dockFolderContextMenuItems(
    folder: FolderItem,
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.LEFT),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.RIGHT),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(folder.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action =
                        LauncherShellAction.MoveDockShortcutToIndex(
                            folder.id,
                            targetIndex = shortcutCount - 1,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to home",
                    action = LauncherShellAction.MoveDockItemToHome(folder.id),
                ),
            )
        } else {
            emptyList()
        }

    return editItems + ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(folder.id))
}

/**
 * An app pair's menu: what a pair does that two apps do not, then what every pinned item has (moving it in the Dock,
 * taking it off). "Unpair" leaves the two apps pinned, first then second, where the pair was.
 */
internal fun dockPairContextMenuItems(
    pair: AppPairItem,
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val pairItems =
        listOf(
            ShortcutContextMenuItem("Open side by side", LauncherShellAction.LaunchAppPair(pair)),
            ShortcutContextMenuItem("Swap sides", LauncherShellAction.SwapDockPair(pair.id)),
            ShortcutContextMenuItem("Open ${pair.first.label}", pair.first.launchAction()),
            ShortcutContextMenuItem("Open ${pair.second.label}", pair.second.launchAction()),
            ShortcutContextMenuItem("Unpair", LauncherShellAction.UnpairDockItem(pair.id)),
        )
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action = LauncherShellAction.MoveDockShortcut(pair.id, DockItemMoveDirection.LEFT),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action = LauncherShellAction.MoveDockShortcut(pair.id, DockItemMoveDirection.RIGHT),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(pair.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action = LauncherShellAction.MoveDockShortcutToIndex(pair.id, targetIndex = shortcutCount - 1),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }
    return pairItems + editItems +
        ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(pair.id))
}

internal fun dockWidgetContextMenuItems(
    widget: WidgetItem,
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(widget.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action =
                        LauncherShellAction.MoveDockShortcutToIndex(
                            widget.id,
                            targetIndex = shortcutCount - 1,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }

    return editItems +
        ShortcutContextMenuItem(
            label = "Remove from dock",
            action = LauncherShellAction.RemoveDockShortcut(widget.id),
        )
}

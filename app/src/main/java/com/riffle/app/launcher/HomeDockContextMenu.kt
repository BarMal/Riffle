package com.riffle.app.launcher

import com.riffle.app.R
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun dockFolderContextMenuItems(
    folder: FolderItem,
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
    text: SettingsText,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_left),
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.LEFT),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_right),
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.RIGHT),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_start),
                    action = LauncherShellAction.MoveDockShortcutToIndex(folder.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_end),
                    action =
                        LauncherShellAction.MoveDockShortcutToIndex(
                            folder.id,
                            targetIndex = shortcutCount - 1,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_home),
                    action = LauncherShellAction.MoveDockItemToHome(folder.id),
                ),
            )
        } else {
            emptyList()
        }

    return editItems +
        ShortcutContextMenuItem(
            text.text(R.string.menu_remove_from_dock),
            LauncherShellAction.RemoveDockShortcut(folder.id),
        )
}

internal fun dockWidgetContextMenuItems(
    widget: WidgetItem,
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
    text: SettingsText,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_left),
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_right),
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_start),
                    action = LauncherShellAction.MoveDockShortcutToIndex(widget.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_end),
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
            label = text.text(R.string.menu_remove_from_dock),
            action = LauncherShellAction.RemoveDockShortcut(widget.id),
        )
}

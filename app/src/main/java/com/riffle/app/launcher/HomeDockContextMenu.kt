package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.WidgetItem

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

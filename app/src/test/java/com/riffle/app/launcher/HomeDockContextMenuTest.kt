package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockContextMenuTest {
    @Test
    fun directDockDragIsLimitedToShortcutsAndFolders() {
        assertEquals(true, DockSlotItemState.Shortcut(shortcut()).isDirectDockDragEligible())
        assertEquals(true, DockSlotItemState.Folder(folder()).isDirectDockDragEligible())
        assertEquals(false, DockSlotItemState.Widget(widget()).isDirectDockDragEligible())
    }

    @Test
    fun dragPreviewReflowsItemsByStableIdWithoutMutatingTheSavedOrder() {
        val camera = shortcut()
        val weather = widget()
        val folder = folder()
        val original = listOf(camera, weather, folder)

        val preview =
            original.dockItemsForPreview(
                DockDragState(itemId = folder.id, originIndex = 2, targetIndex = 0),
            )

        assertEquals(listOf(folder.id, camera.id, weather.id), preview.map { it.id })
        assertEquals(listOf(camera.id, weather.id, folder.id), original.map { it.id })
    }

    @Test
    fun dragPreviewIgnoresAStaleSourceItem() {
        val camera = shortcut()
        val original = listOf(camera)

        assertEquals(
            original,
            original.dockItemsForPreview(
                DockDragState(LauncherItemId("removed"), originIndex = 0, targetIndex = 0),
            ),
        )
    }

    @Test
    fun editDockShortcutMenuAddsMoveActionsAndOmitsEditHome() {
        val shortcut = shortcut()

        val items =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 3,
            )

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(shortcut.id, targetIndex = 0),
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action = LauncherShellAction.MoveDockShortcutToIndex(shortcut.id, targetIndex = 2),
                ),
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Move to home", LauncherShellAction.MoveDockItemToHome(shortcut.id)),
                ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(shortcut.id)),
            ),
            items,
        )
    }

    @Test
    fun editDockShortcutMenuDisablesUnavailableMoveActionsAtBoundaries() {
        val shortcut = shortcut()

        val firstItems =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 0,
                shortcutCount = 2,
            )
        val lastItems =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 2,
            )

        assertEquals(false, firstItems.first { it.label == "Move left" }.enabled)
        assertEquals(true, firstItems.first { it.label == "Move right" }.enabled)
        assertEquals(false, firstItems.first { it.label == "Move to start" }.enabled)
        assertEquals(true, firstItems.first { it.label == "Move to end" }.enabled)
        assertEquals(true, lastItems.first { it.label == "Move left" }.enabled)
        assertEquals(false, lastItems.first { it.label == "Move right" }.enabled)
        assertEquals(true, lastItems.first { it.label == "Move to start" }.enabled)
        assertEquals(false, lastItems.first { it.label == "Move to end" }.enabled)
    }

    @Test
    fun widgetDockMenuRemovesWidgetFromDock() {
        val widget = widget()

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Remove from dock",
                    action = LauncherShellAction.RemoveDockShortcut(widget.id),
                ),
            ),
            dockWidgetContextMenuItems(widget),
        )
    }

    @Test
    fun editWidgetDockMenuAddsMoveActions() {
        val widget = widget()

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = widget.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(widget.id, targetIndex = 0),
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action = LauncherShellAction.MoveDockShortcutToIndex(widget.id, targetIndex = 2),
                ),
                ShortcutContextMenuItem(
                    label = "Remove from dock",
                    action = LauncherShellAction.RemoveDockShortcut(widget.id),
                ),
            ),
            dockWidgetContextMenuItems(
                widget = widget,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 3,
            ),
        )
    }

    @Test
    fun editFolderDockMenuAddsTheSameMoveActions() {
        val folder = folder()

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.LEFT),
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action = LauncherShellAction.MoveDockShortcut(folder.id, DockItemMoveDirection.RIGHT),
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(folder.id, targetIndex = 0),
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action = LauncherShellAction.MoveDockShortcutToIndex(folder.id, targetIndex = 2),
                ),
                ShortcutContextMenuItem(
                    label = "Move to home",
                    action = LauncherShellAction.MoveDockItemToHome(folder.id),
                ),
                ShortcutContextMenuItem(
                    label = "Remove from dock",
                    action = LauncherShellAction.RemoveDockShortcut(folder.id),
                ),
            ),
            dockFolderContextMenuItems(
                folder = folder,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 3,
            ),
        )
    }

    private fun shortcut(): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("camera"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.example.camera"),
                    activityName = AppActivityName(".CameraActivity"),
                ),
            label = "Camera",
        )

    private fun widget(): WidgetItem =
        WidgetItem(
            id = LauncherItemId("dock-widget:42"),
            appWidgetId = HostedWidgetId(42),
            label = "Weather",
        )

    private fun folder(): FolderItem =
        FolderItem(
            id = LauncherItemId("dock-folder:work"),
            label = "Work",
            items = emptyList(),
        )
}

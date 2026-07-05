package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.FolderItemMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderContextMenuTest {
    @Test
    fun folderShortcutMenuContainsManagementActions() {
        val shortcut = shortcut()
        val folder =
            FolderItem(
                id = LauncherItemId("folder"),
                label = "Folder",
                items = listOf(shortcut),
            )

        val items = folderShortcutContextMenuItems(folder = folder, shortcut = shortcut)

        assertEquals(
            listOf(
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem(
                    label = "Move up",
                    action =
                        LauncherShellAction.MoveAppInFolder(
                            folderId = folder.id,
                            itemId = shortcut.id,
                            direction = FolderItemMoveDirection.UP,
                        ),
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Move down",
                    action =
                        LauncherShellAction.MoveAppInFolder(
                            folderId = folder.id,
                            itemId = shortcut.id,
                            direction = FolderItemMoveDirection.DOWN,
                        ),
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Move to home",
                    action =
                        LauncherShellAction.MoveAppOutOfFolder(
                            folderId = folder.id,
                            itemId = shortcut.id,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Remove from folder",
                    action =
                        LauncherShellAction.RemoveAppFromFolder(
                            folderId = folder.id,
                            itemId = shortcut.id,
                        ),
                ),
            ),
            items,
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
}

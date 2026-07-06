package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFolderStateTest {
    @Test
    fun resolvesOpenedFolderFromCurrentSelectedPage() {
        val originalFolder =
            folder(
                id = "folder:tools",
                label = "Tools",
                items = listOf(shortcut(id = "camera", label = "Camera")),
            )
        val updatedFolder =
            originalFolder.copy(
                label = "Utilities",
                items = originalFolder.items + shortcut(id = "calendar", label = "Calendar"),
            )
        val layout = layoutWith(updatedFolder)

        assertEquals(updatedFolder, layout.openedFolder(originalFolder.id))
    }

    @Test
    fun resolvesOpenedFolderFromDockItems() {
        val folder =
            folder(
                id = "folder:dock-tools",
                label = "Dock tools",
                items = listOf(shortcut(id = "camera", label = "Camera")),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(items = listOf(folder)))
            }

        assertEquals(folder, layout.openedFolder(folder.id))
    }

    @Test
    fun returnsNullWhenOpenedFolderNoLongerExists() {
        val layout = layoutWith(shortcut(id = "camera", label = "Camera"))

        assertNull(layout.openedFolder(LauncherItemId("folder:missing")))
    }

    @Test
    fun returnsNullWhenNoFolderIsOpen() {
        val layout = layoutWith(folder(id = "folder:tools", label = "Tools"))

        assertNull(layout.openedFolder(folderId = null))
    }

    private fun layoutWith(vararg items: LauncherItem) =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                pages =
                    listOf(
                        defaults.selectedPage.copy(
                            items = items.toList(),
                        ),
                    ),
            )
        }

    private fun folder(
        id: String,
        label: String,
        items: List<AppShortcutItem> = emptyList(),
    ): FolderItem =
        FolderItem(
            id = LauncherItemId(id),
            label = label,
            items = items,
        )

    private fun shortcut(
        id: String,
        label: String,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:$id"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}

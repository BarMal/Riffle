package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutVisibilityFilterTest {
    @Test
    fun hidesHomeShortcutsForAppsThatAreNotVisible() {
        val camera = app("Camera")
        val docs = app("Docs")
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages =
                        listOf(
                            defaults.selectedPage.copy(
                                items =
                                    listOf(
                                        shortcut(id = "camera", app = camera),
                                        shortcut(id = "docs", app = docs),
                                    ),
                            ),
                        ),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        assertEquals(listOf("Camera"), visibleLayout.selectedPage.items.filterIsInstance<AppShortcutItem>().labels)
        assertEquals(listOf("Camera", "Docs"), layout.selectedPage.items.filterIsInstance<AppShortcutItem>().labels)
    }

    @Test
    fun filtersDockAndFolderItems() {
        val camera = app("Camera")
        val docs = app("Docs")
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = listOf(shortcut(id = "folder-camera", app = camera), shortcut(id = "folder-docs", app = docs)),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages = listOf(defaults.selectedPage.copy(items = listOf(folder))),
                    dock = DockModel(capacity = 5, items = listOf(shortcut(id = "dock-docs", app = docs))),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        val visibleFolder = visibleLayout.selectedPage.items.single() as FolderItem
        assertEquals(listOf("Camera"), visibleFolder.items.labels)
        assertEquals(emptyList<AppShortcutItem>(), visibleLayout.dock.items)
    }

    @Test
    fun removesFoldersWhenAllFolderItemsAreHidden() {
        val docs = app("Docs")
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = listOf(shortcut(id = "folder-docs", app = docs)),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(pages = listOf(defaults.selectedPage.copy(items = listOf(folder))))
            }

        val visibleLayout = layout.visibleTo(apps = emptyList())

        assertEquals(emptyList<AppShortcutItem>(), visibleLayout.selectedPage.items)
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun shortcut(
        id: String,
        app: InstalledApp,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app.identity,
            label = app.label,
        )

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}

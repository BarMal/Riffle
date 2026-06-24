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
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
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
                                        shortcut(
                                            id = "camera",
                                            app = camera,
                                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                        ),
                                        shortcut(
                                            id = "docs",
                                            app = docs,
                                            placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        val visibleShortcut = visibleLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals("Camera", visibleShortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), visibleShortcut.placement)
        assertEquals(listOf("Camera", "Docs"), layout.selectedPage.items.filterIsInstance<AppShortcutItem>().labels)
    }

    @Test
    fun preservesVisibleHomeShortcutPlacements() {
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
                                        shortcut(
                                            id = "docs",
                                            app = docs,
                                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                        ),
                                        shortcut(
                                            id = "camera",
                                            app = camera,
                                            placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        val visibleShortcut = visibleLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals("Camera", visibleShortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 1, row = 0)), visibleShortcut.placement)
        assertEquals(GridPlacement(cell = GridCell(column = 1, row = 0)), (layout.selectedPage.items[1]).placement)
    }

    @Test
    fun preservesVisibleWidgetAnchorAndSpan() {
        val camera = app("Camera")
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:clock"),
                appWidgetId = HostedWidgetId(42),
                label = "Clock",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 2, row = 1),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages =
                        listOf(
                            defaults.selectedPage.copy(
                                items =
                                    listOf(
                                        shortcut(
                                            id = "camera",
                                            app = camera,
                                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                        ),
                                        widget,
                                    ),
                            ),
                        ),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        val visibleWidget = visibleLayout.selectedPage.items.filterIsInstance<WidgetItem>().single()
        assertEquals(widget.placement, visibleWidget.placement)
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

    @Test
    fun removesTrailingEmptyAllAppsPagesAfterFiltering() {
        val camera = app("Camera")
        val docs = app("Docs")
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    pages =
                        listOf(
                            defaults.selectedPage.copy(items = listOf(shortcut(id = "camera", app = camera))),
                            LauncherPage(
                                id = LauncherPageId("library:1"),
                                type = LauncherPageType.AllApps,
                                grid = defaults.settings.grid.dimensions,
                                items = listOf(shortcut(id = "library-docs", app = docs)),
                            ),
                        ),
                    selectedPageId = LauncherPageId("library:1"),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        assertEquals(listOf(LauncherPageId("home")), visibleLayout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home"), visibleLayout.selectedPageId)
    }

    @Test
    fun keepsTrailingEmptyHomePagesInLibraryModeAfterFiltering() {
        val camera = app("Camera")
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    pages =
                        listOf(
                            defaults.selectedPage.copy(items = listOf(shortcut(id = "camera", app = camera))),
                            LauncherPage(
                                id = LauncherPageId("spare"),
                                grid = defaults.settings.grid.dimensions,
                            ),
                        ),
                    selectedPageId = LauncherPageId("spare"),
                )
            }

        val visibleLayout = layout.visibleTo(apps = listOf(camera))

        assertEquals(
            listOf(LauncherPageId("home"), LauncherPageId("spare")),
            visibleLayout.pages.map { page -> page.id },
        )
        assertEquals(LauncherPageId("spare"), visibleLayout.selectedPageId)
    }

    @Test
    fun keepsEmptyFoldersVisible() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages =
                        listOf(
                            defaults.selectedPage.copy(
                                items =
                                    listOf(
                                        FolderItem(
                                            id = LauncherItemId("folder:empty"),
                                            label = "Folder",
                                            items = emptyList(),
                                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val visibleLayout = layout.visibleTo(apps = emptyList())

        val folder = visibleLayout.selectedPage.items.single() as FolderItem
        assertEquals("Folder", folder.label)
        assertEquals(emptyList<AppShortcutItem>(), folder.items)
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
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app.identity,
            label = app.label,
            placement = placement,
        )

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}

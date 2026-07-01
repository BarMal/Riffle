package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellFolderViewModelTest {
    @Test
    fun createsHomeFolderFromShortcutsAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        viewModel.onAddAppToHome(calendar)
        val shortcuts = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateHomeFolder(
                itemIds = shortcuts.map { shortcut -> shortcut.id },
                label = "Folder",
            ),
        )

        val folder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals("Folder", folder.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), folder.placement)
        assertEquals(listOf(camera.identity, calendar.identity), folder.items.map { item -> item.appIdentity })
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun createsEmptyHomeFolderAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
        )

        val folder = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<FolderItem>().single()
        assertEquals("Folder", folder.label)
        assertEquals(emptyList<AppShortcutItem>(), folder.items)
        assertEquals(
            GridPlacement(cell = GridCell(column = 1, row = 0)),
            folder.placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun createsEmptyHomeFolderInLibraryModeAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar)),
                homeLayoutRepository = repository,
            )
        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
        )

        val folder = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<FolderItem>().single()
        assertEquals(emptyList<AppShortcutItem>(), folder.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun createsEmptyHomeFolderOnNewPageWhenSelectedPageIsFull() {
        val camera = app(label = "Camera")
        val fullPage =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 1, rows = 1),
                items =
                    listOf(
                        AppShortcutItem(
                            id = LauncherItemId("app:camera"),
                            appIdentity = camera.identity,
                            label = camera.label,
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                    ),
            )
        val repository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages = listOf(fullPage),
                        selectedPageId = fullPage.id,
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
        )

        val layout = viewModel.state.value.homeLayout
        val folder = layout.selectedPage.items.single() as FolderItem
        assertEquals(listOf(LauncherPageId("home"), LauncherPageId("home-2")), layout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home-2"), layout.selectedPageId)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), folder.placement)
        assertEquals(emptyList<AppShortcutItem>(), folder.items)
        assertEquals(layout, repository.savedLayout)
    }

    @Test
    fun createsHomeFolderFromLibraryModeShortcutsAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar)),
                homeLayoutRepository = repository,
            )
        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))
        val shortcuts = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateHomeFolder(
                itemIds = shortcuts.map { shortcut -> shortcut.id },
                label = "Folder",
            ),
        )

        val folder =
            viewModel.state.value.homeLayout.selectedPage.items.single { item -> item is FolderItem } as FolderItem
        assertEquals(
            setOf(camera.identity, calendar.identity),
            folder.items.map { item -> item.appIdentity }.toSet(),
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun renamesHomeFolderAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        viewModel.onAddAppToHome(calendar)
        val shortcuts = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
        viewModel.onHomeShortcutEdited(
            LauncherShellAction.CreateHomeFolder(
                itemIds = shortcuts.map { shortcut -> shortcut.id },
                label = "Folder",
            ),
        )
        val folder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.RenameHomeFolder(
                itemId = folder.id,
                label = "Tools",
            ),
        )

        val renamedFolder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals("Tools", renamedFolder.label)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun addsAppToHomeFolderAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val maps = app(label = "Maps")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar, maps)),
                homeLayoutRepository = repository,
            )
        val folder = viewModel.createFolder(camera, calendar)

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.AddAppToFolder(
                folderId = folder.id,
                app = maps,
            ),
        )

        val updatedFolder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals(
            listOf(camera.identity, calendar.identity, maps.identity),
            updatedFolder.items.map { item -> item.appIdentity },
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun removesAppFromHomeFolderAndSavesLayout() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, calendar)),
                homeLayoutRepository = repository,
            )
        val folder = viewModel.createFolder(camera, calendar)
        val cameraShortcut = folder.items.first { item -> item.appIdentity == camera.identity }

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.RemoveAppFromFolder(
                folderId = folder.id,
                itemId = cameraShortcut.id,
            ),
        )

        val updatedFolder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals(listOf(calendar.identity), updatedFolder.items.map { item -> item.appIdentity })
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeInstalledAppRepository(
        private val apps: List<InstalledApp>,
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            visibility = visibility,
        )

    private fun LauncherShellViewModel.createFolder(
        firstApp: InstalledApp,
        secondApp: InstalledApp,
    ): FolderItem {
        onAddAppToHome(firstApp)
        onAddAppToHome(secondApp)
        val shortcuts = state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
        onHomeShortcutEdited(
            LauncherShellAction.CreateHomeFolder(
                itemIds = shortcuts.map { shortcut -> shortcut.id },
                label = "Folder",
            ),
        )

        return state.value.homeLayout.selectedPage.items.single() as FolderItem
    }
}

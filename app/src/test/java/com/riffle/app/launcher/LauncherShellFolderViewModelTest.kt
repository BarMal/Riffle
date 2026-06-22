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
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
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

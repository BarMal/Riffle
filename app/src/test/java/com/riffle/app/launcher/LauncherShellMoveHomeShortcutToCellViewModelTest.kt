package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellMoveHomeShortcutToCellViewModelTest {
    @Test
    fun movesHomeShortcutToCellAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = shortcut.id,
                cell = GridCell(column = 2, row = 1),
            ),
        )

        assertEquals(
            GridPlacement(cell = GridCell(column = 2, row = 1)),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun reflowsCompactLibraryPagesAfterMovingGeneratedShortcut() {
        val apps = listOf(app(label = "Camera"), app(label = "Calendar"), app(label = "Docs"))
        val compactGrid = GridDimensions(columns = 2, rows = 1)
        val libraryLayout =
            HomeLayoutDefaults.standard()
                .copy(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(grid = compactGrid)),
                    settings =
                        HomeLayoutDefaults.standard().settings.copy(
                            grid = GridSettings(dimensions = compactGrid, compactLibraryPages = true),
                        ),
                )
                .withHomeScreenLibraryApps(apps)
                .copy(selectedPageId = LauncherPageId("library:1"))
        val repository = FakeHomeLayoutRepository(savedLayout = libraryLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = apps),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(viewModeAvailability = libraryViewModeAvailability),
            )
        val docs = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = docs.id,
                cell = GridCell(column = 1, row = 0),
            ),
        )

        val updatedLayout = viewModel.state.value.homeLayout
        assertEquals(LauncherPageId("library:1"), updatedLayout.selectedPageId)
        assertEquals(
            GridPlacement(cell = GridCell(column = 0, row = 0)),
            updatedLayout.selectedPage.items.single().placement,
        )
        assertEquals(listOf(2, 1), updatedLayout.pages.map { page -> page.items.size })
        assertEquals(updatedLayout, repository.savedLayout)
    }

    @Test
    fun droppingAppShortcutOntoAppShortcutCreatesFolder() {
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
        val source = shortcuts.single { shortcut -> shortcut.appIdentity == camera.identity }
        val target = shortcuts.single { shortcut -> shortcut.appIdentity == calendar.identity }

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = source.id,
                cell = target.placement?.cell ?: error("Target shortcut should be placed"),
            ),
        )

        val folder = viewModel.state.value.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals("Folder", folder.label)
        assertEquals(target.placement, folder.placement)
        assertEquals(listOf(calendar.identity, camera.identity), folder.items.map { item -> item.appIdentity })
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun movesHomeWidgetToEmptyCellAndSavesLayout() {
        val widget =
            WidgetItem(
                id = com.riffle.core.domain.launcher.home.LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val repository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(widget))),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = widget.id,
                cell = GridCell(column = 2, row = 1),
            ),
        )

        assertEquals(
            GridPlacement(cell = GridCell(column = 2, row = 1)),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun droppingHomeWidgetOntoShortcutSwapsItemsWithoutCreatingFolder() {
        val widget =
            WidgetItem(
                id = com.riffle.core.domain.launcher.home.LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val camera = appShortcutItem(label = "Camera", cell = GridCell(column = 1, row = 0))
        val initialLayout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(widget, camera))),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = initialLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = widget.id,
                cell = GridCell(column = 1, row = 0),
            ),
        )

        val updatedItems = viewModel.state.value.homeLayout.selectedPage.items
        val movedWidget = updatedItems.filterIsInstance<WidgetItem>().single()
        val movedShortcut = updatedItems.filterIsInstance<AppShortcutItem>().single()

        assertEquals(GridPlacement(cell = GridCell(column = 1, row = 0)), movedWidget.placement)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), movedShortcut.placement)
        assertEquals(0, updatedItems.filterIsInstance<FolderItem>().size)
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

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun appShortcutItem(
        label: String,
        cell: GridCell,
    ): AppShortcutItem =
        AppShortcutItem(
            id = com.riffle.core.domain.launcher.home.LauncherItemId("app:${label.lowercase()}"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            placement = GridPlacement(cell = cell),
        )

    private val libraryViewModeAvailability =
        LauncherViewModeAvailability(
            enabledExperimentalModesByDeviceClass =
                mapOf(HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)),
        )
}

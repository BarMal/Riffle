package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
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
}

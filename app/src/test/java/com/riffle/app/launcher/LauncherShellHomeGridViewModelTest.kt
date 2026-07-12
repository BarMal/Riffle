package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellHomeGridViewModelTest {
    @Test
    fun updatesHomeGridMarginsAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridMargin(horizontalDp = 8, verticalDp = 20),
        )

        assertEquals(
            GridInsets(start = 8, top = 20, end = 8, bottom = 20),
            viewModel.state.value.homeLayout.settings.grid.margin,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesHomeGridDimensionsAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 5, rows = 6)),
        )

        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6), GridDimensions(columns = 5, rows = 6)),
            viewModel.state.value.homeLayout.pages.map { page -> page.grid },
        )
        assertEquals(
            GridDimensions(columns = 5, rows = 6),
            viewModel.state.value.homeLayout.settings.grid.dimensions,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresHomeGridDimensionsThatWouldClipItems() {
        val camera = app(label = "Camera")
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera:home"),
                appIdentity = camera.identity,
                label = camera.label,
                placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
            )
        val savedLayout =
            HomeLayoutDefaults.standard().let { layout ->
                layout.copy(
                    pages = listOf(layout.selectedPage.copy(items = listOf(shortcut))),
                )
            }
        val repository = FakeHomeLayoutRepository(savedLayout = savedLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        val layoutBeforeResize = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 3, rows = 5)),
        )

        assertEquals(layoutBeforeResize, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeResize, repository.savedLayout)
    }

    @Test
    fun enablingLibraryPageCompactionImmediatelyReflowsGeneratedApps() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val grid = GridDimensions(columns = 2, rows = 1)
        val savedLayout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = grid,
                            items = listOf(camera.libraryShortcut()),
                        ),
                        LauncherPage(
                            id = LauncherPageId("library:1"),
                            grid = grid,
                            items = listOf(clock.libraryShortcut()),
                        ),
                        LauncherPage(
                            id = LauncherPageId("library:2"),
                            grid = grid,
                            items = listOf(calendar.libraryShortcut()),
                        ),
                    ),
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        grid = GridSettings(dimensions = grid),
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = savedLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(listOf(camera, calendar, clock)),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(viewModeAvailability = libraryViewModeAvailability),
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectLibraryPageCompaction(enabled = true))

        assertEquals(
            listOf(LauncherPageId("home"), LauncherPageId("library:1")),
            viewModel.state.value.homeLayout.pages.map { page -> page.id },
        )
        assertEquals(
            listOf(camera.identity, clock.identity),
            viewModel.state.value.homeLayout.pages[0].items.appIdentities,
        )
        assertEquals(listOf(calendar.identity), viewModel.state.value.homeLayout.pages[1].items.appIdentities)
        assertEquals(true, viewModel.state.value.homeLayout.settings.grid.compactLibraryPages)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun compactLibraryPagesReflowGeneratedAppsAfterGridChanges() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val compactGrid = GridDimensions(columns = 2, rows = 1)
        val expandedGrid = GridDimensions(columns = 3, rows = 1)
        val savedLayout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = compactGrid,
                        ),
                    ),
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        grid = GridSettings(dimensions = compactGrid),
                    ),
            ).withHomeScreenLibraryApps(listOf(camera, calendar, clock))
        val repository = FakeHomeLayoutRepository(savedLayout = savedLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(listOf(camera, calendar, clock)),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(viewModeAvailability = libraryViewModeAvailability),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onHomePageEdited(LauncherShellAction.SelectLibraryPageCompaction(enabled = true))
        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeGridDimensions(expandedGrid))

        assertEquals(listOf(LauncherPageId("home")), viewModel.state.value.homeLayout.pages.map { page -> page.id })
        assertEquals(
            listOf(calendar.identity, camera.identity, clock.identity),
            viewModel.state.value.homeLayout.selectedPage.items.appIdentities,
        )
        assertEquals(true, viewModel.state.value.homeLayout.settings.grid.compactLibraryPages)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private class FakeInstalledAppRepository(
        private val apps: List<InstalledApp>,
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
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

    private val List<Any>.appIdentities: List<AppIdentity>
        get() = filterIsInstance<AppShortcutItem>().map { item -> item.appIdentity }

    private val libraryViewModeAvailability =
        LauncherViewModeAvailability(
            enabledExperimentalModesByDeviceClass =
                mapOf(HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)),
        )
}

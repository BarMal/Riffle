package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherHomePageEditReducerTest {
    @Test
    fun appliesHomePageEngineEditsAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(repository.savedLayoutSet)

        val updated = reducer.reduce(state, LauncherShellAction.AddHomePage)

        assertEquals(listOf(LauncherPageId("home"), LauncherPageId("home-2")), updated.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), updated.homeLayout.selectedPageId)
        assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun launcherViewModeSelectionReflowsHomeScreenLibraryApps() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(repository.savedLayoutSet).copy(installedApps = listOf(camera))

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
            )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, updated.homeLayout.viewMode)
        assertEquals(camera.identity, updated.homeLayout.selectedPage.items.singleAppShortcut().appIdentity)
        assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun deviceClassSelectionUsesTheSelectedDeviceLayout() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
        val phoneLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE)
        val foldableLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)
                .copy(
                    pages =
                        listOf(
                            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)
                                .selectedPage
                                .copy(id = LauncherPageId("foldable-home")),
                        ),
                    selectedPageId = LauncherPageId("foldable-home"),
                )
        val layoutSet =
            HomeLayoutSet(
                activeKey = phoneKey,
                layouts = mapOf(phoneKey to phoneLayout, foldableKey to foldableLayout),
            )
        val repository = FakeHomeLayoutRepository(layoutSet)
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(layoutSet)

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectHomeLayoutDeviceClass(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
                ),
            )

        assertEquals(foldableKey, updated.homeLayoutSet.activeKey)
        assertEquals(LauncherPageId("foldable-home"), updated.homeLayout.selectedPageId)
        assertEquals(foldableKey, repository.savedLayoutSet?.activeKey)
    }

    @Test
    fun settingsTargetLayoutEditsDoNotChangeActiveHomeLayout() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
        val phoneGrid = GridDimensions(columns = 4, rows = 5)
        val foldableGrid = GridDimensions(columns = 5, rows = 6)
        val updatedFoldableGrid = GridDimensions(columns = 6, rows = 7)
        val phoneLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE)
                .withGrid(phoneGrid)
        val foldableLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)
                .withGrid(foldableGrid)
        val layoutSet =
            HomeLayoutSet(
                activeKey = phoneKey,
                layouts = mapOf(phoneKey to phoneLayout, foldableKey to foldableLayout),
            )
        val repository = FakeHomeLayoutRepository(layoutSet)
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state =
            launcherState(layoutSet)
                .copy(
                    destination = ShellDestination.SETTINGS,
                    settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    availableLayoutDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
                )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectHomeGridDimensions(updatedFoldableGrid),
            )

        val savedLayoutSet = checkNotNull(repository.savedLayoutSet)
        assertEquals(phoneLayout, updated.homeLayout)
        assertEquals(phoneGrid, savedLayoutSet.layoutFor(phoneKey).settings.grid.dimensions)
        assertEquals(updatedFoldableGrid, savedLayoutSet.layoutFor(foldableKey).settings.grid.dimensions)
        assertEquals(phoneKey, savedLayoutSet.activeKey)
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null

        constructor(savedLayout: HomeLayout) {
            savedLayoutSet = HomeLayoutSet.fromLayout(savedLayout)
        }

        constructor(savedLayoutSet: HomeLayoutSet) {
            this.savedLayoutSet = savedLayoutSet
        }

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet =
                savedLayoutSet
                    ?.withActiveLayout(layout)
                    ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private fun launcherState(layoutSet: HomeLayoutSet?): LauncherShellState {
        val activeLayout = layoutSet?.activeLayout ?: HomeLayoutDefaults.standard()
        return LauncherShellState(
            homeLayout = activeLayout,
            homeLayoutSet = layoutSet ?: HomeLayoutSet.fromLayout(activeLayout),
        )
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

    private fun HomeLayout.withGrid(dimensions: GridDimensions): HomeLayout =
        copy(
            pages = pages.map { page -> page.copy(grid = dimensions) },
            settings = settings.copy(grid = GridSettings(dimensions = dimensions)),
        )

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }

    private fun List<Any>.singleAppShortcut(): AppShortcutItem = single() as AppShortcutItem
}

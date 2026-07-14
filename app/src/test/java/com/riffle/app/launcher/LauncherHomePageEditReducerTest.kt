package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WidgetItem
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
    fun selectingCategoryPageImmediatelyPopulatesCategorizedInstalledApps() {
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val camera = app(label = "Camera", category = "Image")
        val music = app(label = "Music", category = "Audio")
        val uncategorized = app(label = "Notes")
        val state = launcherState(repository.savedLayoutSet).copy(installedApps = listOf(camera, music, uncategorized))

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectSelectedHomePageType(
                    LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY),
                ),
            )

        assertEquals(
            LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY),
            updated.homeLayout.selectedPage.type,
        )
        assertEquals(
            listOf(music.identity, camera.identity),
            updated.homeLayout.selectedPage.items.map { item -> (item as AppShortcutItem).appIdentity },
        )
        assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun persistsReorderedPageOverviewLayout() {
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        var state = launcherState(repository.savedLayoutSet)

        state = reducer.reduce(state, LauncherShellAction.AddHomePage)
        state = reducer.reduce(state, LauncherShellAction.AddHomePage)
        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.MoveHomePage(
                    pageId = LauncherPageId("home-3"),
                    targetIndex = 0,
                ),
            )

        assertEquals(
            listOf(LauncherPageId("home-3"), LauncherPageId("home"), LauncherPageId("home-2")),
            updated.homeLayout.pageIds,
        )
        assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun rejectedPageDeleteLeavesStateAndStoredLayoutUntouched() {
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(repository.savedLayoutSet)
        val savedBefore = checkNotNull(repository.savedLayoutSet)

        val updated = reducer.reduce(state, LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(state, updated)
        assertEquals(savedBefore, repository.savedLayoutSet)
    }

    @Test
    fun rejectedPageDuplicationLeavesStateAndStoredLayoutUntouched() {
        val widget =
            WidgetItem(
                id = com.riffle.core.domain.launcher.home.LauncherItemId("weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0), span = GridSpan(columns = 2, rows = 2)),
            )
        val initialLayout =
            HomeLayoutDefaults.standard()
                .copy(
                    pages =
                        listOf(
                            HomeLayoutDefaults.standard()
                                .selectedPage
                                .copy(items = listOf(widget)),
                        ),
                    editMode = HomeEditMode.ManagingPages,
                )
        val repository = FakeHomeLayoutRepository(initialLayout)
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(repository.savedLayoutSet)
        val savedBefore = checkNotNull(repository.savedLayoutSet)

        val updated = reducer.reduce(state, LauncherShellAction.DuplicateSelectedHomePage)

        assertEquals(state, updated)
        assertEquals(savedBefore, repository.savedLayoutSet)
    }

    @Test
    fun launcherViewModeSelectionReflowsHomeScreenLibraryApps() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val reducer =
            LauncherHomePageEditReducer(
                homeLayoutRepository = repository,
                viewModeAvailability =
                    LauncherViewModeAvailability(
                        enabledExperimentalModesByDeviceClass =
                            mapOf(HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)),
                    ),
            )
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
    fun templateSelectionAppliesAndPersistsEachDefaultTemplatesSeedLayout() {
        val selections =
            listOf(
                LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId to LauncherViewMode.STANDARD_APP_DRAWER,
                LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId to LauncherViewMode.HOME_SCREEN_LIBRARY,
                LauncherTemplateCatalogDefaults.cardInterfaceId to LauncherViewMode.CARD_INTERFACE,
            )

        selections.forEach { (templateId, mode) ->
            val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
            val reducer =
                LauncherHomePageEditReducer(
                    homeLayoutRepository = repository,
                    viewModeAvailability =
                        LauncherViewModeAvailability(
                            enabledExperimentalModesByDeviceClass =
                                mapOf(
                                    HomeLayoutDeviceClass.PHONE to
                                        setOf(
                                            LauncherViewMode.HOME_SCREEN_LIBRARY,
                                            LauncherViewMode.CARD_INTERFACE,
                                        ),
                                ),
                        ),
                )
            val updated =
                reducer.reduce(
                    launcherState(repository.savedLayoutSet),
                    LauncherShellAction.SelectLauncherTemplate(templateId = templateId, mode = mode),
                )
            val template = LauncherTemplateCatalogDefaults.templates.first { it.id == templateId }

            assertEquals(templateId, updated.homeLayout.templateId)
            assertEquals(mode, updated.homeLayout.viewMode)
            assertEquals(template.seedPageTypes, updated.homeLayout.pages.map { page -> page.type })
            assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
        }
    }

    @Test
    fun standardTemplateUsesDedicatedAppDrawerForInstalledApps() {
        val apps = listOf(app(label = "Camera"), app(label = "Calendar"))
        val repository = FakeHomeLayoutRepository(HomeLayoutDefaults.standard())
        val selectedTemplate =
            LauncherHomePageEditReducer(homeLayoutRepository = repository).reduce(
                launcherState(repository.savedLayoutSet).copy(installedApps = apps),
                LauncherShellAction.SelectLauncherTemplate(
                    templateId = LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId,
                    mode = LauncherViewMode.STANDARD_APP_DRAWER,
                ),
            )

        assertEquals(listOf(LauncherPageType.Home), selectedTemplate.homeLayout.pages.map { page -> page.type })

        val drawerState =
            LauncherShellStateReducer().navigationActionSelected(
                selectedTemplate,
                ShellNavigationAction.OpenAppDrawer,
            )
        val populatedDrawer =
            checkNotNull(
                LauncherAppListActionReducer(InstalledAppCatalog()).reduce(
                    drawerState,
                    LauncherShellAction.AppDrawerQueryChanged(""),
                ),
            )

        assertEquals(ShellDestination.APP_DRAWER, populatedDrawer.destination)
        assertEquals(listOf("Calendar", "Camera"), populatedDrawer.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun unavailableLauncherViewModeSelectionFallsBackToStandardWithoutMutatingStoredLayout() {
        val cardKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)
        val cardLayout =
            HomeLayoutDefaults.standard()
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet.standard()
                .withLayout(cardKey, cardLayout)
        val repository = FakeHomeLayoutRepository(layoutSet)
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(layoutSet)

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.CARD_INTERFACE),
            )

        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, updated.homeLayout.viewMode)
        assertEquals(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER), repository.savedLayoutSet?.activeKey)
        assertEquals(cardLayout, repository.savedLayoutSet?.layoutFor(cardKey))
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
    fun deviceClassSelectionFallsBackWhenPreferredModeIsUnavailable() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val tabletStandardKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            )
        val tabletCardKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.CARD_INTERFACE,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            )
        val tabletCardLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.TABLET)
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet(
                activeKey = phoneKey,
                layouts =
                    mapOf(
                        phoneKey to HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE),
                        tabletCardKey to tabletCardLayout,
                    ),
                preferredModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to LauncherViewMode.STANDARD_APP_DRAWER,
                        HomeLayoutDeviceClass.TABLET to LauncherViewMode.CARD_INTERFACE,
                    ),
            )
        val repository = FakeHomeLayoutRepository(layoutSet)
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = repository)
        val state = launcherState(layoutSet)

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SelectHomeLayoutDeviceClass(
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                    availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.TABLET),
                ),
            )

        assertEquals(tabletStandardKey, updated.homeLayoutSet.activeKey)
        assertEquals(tabletStandardKey, repository.savedLayoutSet?.activeKey)
        assertEquals(tabletCardLayout, repository.savedLayoutSet?.layoutFor(tabletCardKey))
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            repository.savedLayoutSet?.preferredModesByDeviceClass?.get(HomeLayoutDeviceClass.TABLET),
        )
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

    private fun app(
        label: String,
        category: String? = null,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            category = category,
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

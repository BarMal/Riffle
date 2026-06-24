package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenLibraryLayoutTest {
    @Test
    fun standardModeDoesNotMaterializeInstalledApps() {
        val camera = app(label = "Camera")

        val layout = HomeLayoutDefaults.standard().withHomeScreenLibraryApps(listOf(camera))

        assertEquals(emptyList<AppShortcutItem>(), layout.selectedPage.items)
    }

    @Test
    fun libraryModeAddsMissingInstalledAppsToHomeCells() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 2, rows = 1),
                        ),
                    ),
            )

        val libraryLayout = layout.withHomeScreenLibraryApps(listOf(camera, calendar))

        assertEquals(
            listOf(camera.identity, calendar.identity),
            libraryLayout.selectedPage.items.filterIsInstance<AppShortcutItem>().map { item -> item.appIdentity },
        )
        assertEquals(
            listOf(GridCell(column = 0, row = 0), GridCell(column = 1, row = 0)),
            libraryLayout.selectedPage.items.map { item -> item.placement?.cell },
        )
    }

    @Test
    fun libraryModeKeepsExistingHomeAppsAndAddsOnlyMissingApps() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val existingCamera =
            AppShortcutItem(
                id = LauncherItemId("camera"),
                appIdentity = camera.identity,
                label = camera.label,
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 2, rows = 1),
                            items = listOf(existingCamera),
                        ),
                    ),
            )

        val libraryLayout = layout.withHomeScreenLibraryApps(listOf(camera, calendar))

        assertEquals(
            listOf(camera.identity, calendar.identity),
            libraryLayout.selectedPage.items.filterIsInstance<AppShortcutItem>().map { item -> item.appIdentity },
        )
    }

    @Test
    fun libraryModeCreatesAllAppsOverflowPages() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 1, rows = 1),
                        ),
                    ),
            )

        val libraryLayout = layout.withHomeScreenLibraryApps(listOf(camera, calendar))

        assertEquals(
            listOf(LauncherPageId("home"), LauncherPageId("library:1")),
            libraryLayout.pages.map { page -> page.id },
        )
        assertEquals(LauncherPageType.AllApps, libraryLayout.pages[1].type)
        assertEquals(calendar.identity, (libraryLayout.pages[1].items.single() as AppShortcutItem).appIdentity)
    }

    @Test
    fun sparseLibraryModeAllowsIncompletePagesAfterGridExpansion() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val compactGrid = GridDimensions(columns = 2, rows = 1)
        val expandedGrid = GridDimensions(columns = 3, rows = 1)
        val layout =
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
            )

        val initialLibraryLayout = layout.withHomeScreenLibraryApps(listOf(camera, calendar, clock))
        val expandedLayout =
            initialLibraryLayout
                .copy(
                    pages = initialLibraryLayout.pages.map { page -> page.copy(grid = expandedGrid) },
                    settings = layout.settings.copy(grid = GridSettings(dimensions = expandedGrid)),
                ).withHomeScreenLibraryApps(listOf(camera, calendar, clock))

        assertEquals(listOf(2, 1), expandedLayout.pages.map { page -> page.items.size })
        assertEquals(
            listOf(LauncherPageId("home"), LauncherPageId("library:1")),
            expandedLayout.pages.map { page -> page.id },
        )
    }

    @Test
    fun libraryModeRemovesTrailingEmptyAllAppsPages() {
        val camera = app(label = "Camera")
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 1, rows = 1),
                            items =
                                listOf(
                                    AppShortcutItem(
                                        id = LauncherItemId("library-app:personal:com.riffle.camera/.MainActivity"),
                                        appIdentity = camera.identity,
                                        label = camera.label,
                                        placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                    ),
                                ),
                        ),
                        LauncherPage(
                            id = LauncherPageId("library:1"),
                            type = LauncherPageType.AllApps,
                            grid = GridDimensions(columns = 1, rows = 1),
                        ),
                    ),
                selectedPageId = LauncherPageId("library:1"),
            )

        val libraryLayout = layout.withHomeScreenLibraryApps(listOf(camera))

        assertEquals(listOf(LauncherPageId("home")), libraryLayout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home"), libraryLayout.selectedPageId)
    }

    @Test
    fun compactLibraryModeCollapsesGeneratedAppsAfterGridExpansion() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val compactGrid = GridDimensions(columns = 2, rows = 1)
        val expandedGrid = GridDimensions(columns = 3, rows = 1)
        val layout =
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
                        grid =
                            GridSettings(
                                dimensions = compactGrid,
                                compactLibraryPages = true,
                            ),
                    ),
            )

        val initialLibraryLayout = layout.withHomeScreenLibraryApps(listOf(camera, calendar, clock))
        val expandedLayout =
            initialLibraryLayout
                .copy(
                    pages = initialLibraryLayout.pages.map { page -> page.copy(grid = expandedGrid) },
                    settings =
                        layout.settings.copy(
                            grid =
                                GridSettings(
                                    dimensions = expandedGrid,
                                    compactLibraryPages = true,
                                ),
                        ),
                ).withHomeScreenLibraryApps(listOf(camera, calendar, clock))

        assertEquals(listOf(LauncherPageId("home")), expandedLayout.pages.map { page -> page.id })
        assertEquals(
            listOf(camera.identity, calendar.identity, clock.identity),
            expandedLayout.selectedPage.items.appIdentities,
        )
        assertEquals(
            listOf(GridCell(column = 0, row = 0), GridCell(column = 1, row = 0), GridCell(column = 2, row = 0)),
            expandedLayout.selectedPage.items.map { item -> item.placement?.cell },
        )
    }

    @Test
    fun removesGeneratedLibraryAppsWhenLeavingLibraryMode() {
        val camera = app(label = "Camera")
        val manualCalendar = app(label = "Calendar")
        val manualShortcut =
            AppShortcutItem(
                id = LauncherItemId("manual-calendar"),
                appIdentity = manualCalendar.identity,
                label = manualCalendar.label,
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 2, rows = 1),
                            items = listOf(manualShortcut),
                        ),
                    ),
            )

        val cleanedLayout =
            layout
                .withHomeScreenLibraryApps(listOf(camera, manualCalendar))
                .withoutHomeScreenLibraryApps()

        assertEquals(listOf(manualCalendar.identity), cleanedLayout.selectedPage.items.appIdentities)
    }

    @Test
    fun removesGeneratedAllAppsPagesWhenLeavingLibraryMode() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        LauncherPage(
                            id = LauncherPageId("home"),
                            grid = GridDimensions(columns = 1, rows = 1),
                        ),
                    ),
            )

        val cleanedLayout =
            layout
                .withHomeScreenLibraryApps(listOf(camera, calendar))
                .copy(selectedPageId = LauncherPageId("library:1"))
                .withoutHomeScreenLibraryApps()

        assertEquals(listOf(LauncherPageId("home")), cleanedLayout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home"), cleanedLayout.selectedPageId)
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
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
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

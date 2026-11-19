package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomeLayoutSettings
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library view mode is retired (ADR 0003, Amendment 10), through the shell: a stored library layout, as an older
 * build wrote it (decoded from its stored form), becomes the Standard Home as placed items, and from then on a removal,
 * a move, a refresh, a restart, Simple mode and a new install leave the arrangement as the user made it.
 */
class LauncherShellLibraryRetirementViewModelTest {
    private class Apps(var apps: List<InstalledApp>) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    /** Stores the whole set, as the real store does, so a restart can read back what the shell wrote. */
    private class Sets(var saved: HomeLayoutSet? = null) : HomeLayoutRepository {
        var writes = 0

        override fun loadHomeLayout(): HomeLayout? = saved?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            saveHomeLayoutSet(HomeLayoutSet.fromLayout(layout))
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = saved

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            writes++
            saved = layoutSet
        }
    }

    private class FirstRun : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private val grid = GridDimensions(4, 2)
    private val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")

    private fun identity(label: String) =
        AppIdentity(
            AppPackageName("com.example.${label.lowercase()}"),
            AppActivityName(".Main"),
            AppProfile.personal(),
        )

    private fun app(label: String) = InstalledApp(identity(label), label)

    private fun libraryItem(
        label: String,
        index: Int,
    ) = AppShortcutItem(
        id = LauncherItemId("library-app:${label.lowercase()}"),
        appIdentity = identity(label),
        label = label,
        placement = GridPlacement(GridCell(index % grid.columns, index / grid.columns)),
    )

    private fun libraryLayout(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        compact: Boolean = true,
    ) = HomeLayout(
        viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
        pages =
            listOf(
                LauncherPage(
                    id = LauncherPageId("home"),
                    grid = grid,
                    items = names.take(3).mapIndexed { index, name -> libraryItem(name, index) },
                ),
                LauncherPage(
                    id = LauncherPageId("library:1"),
                    type = LauncherPageType.AllApps,
                    grid = grid,
                    items = names.drop(3).mapIndexed { index, name -> libraryItem(name, index) },
                ),
            ),
        selectedPageId = LauncherPageId("home"),
        dock = DockModel(capacity = 5),
        settings =
            HomeLayoutSettings.standard(
                deviceClass,
            ).copy(grid = GridSettings(grid, compactLibraryPages = compact)),
    )

    /** What a build before the retirement stored: encoded by the codec, whose format has not changed. */
    private fun storedLibrarySet(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        compact: Boolean = true,
        oldestFormat: Boolean = false,
    ): HomeLayoutSet {
        val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, deviceClass)
        val set = HomeLayoutSet(activeKey = key, layouts = mapOf(key to libraryLayout(deviceClass, compact)))
        val json = JSONObject(encodeHomeLayoutSet(set))
        if (oldestFormat) {
            // Oldest builds wrote no page type: the "library:" id prefix said All apps.
            val layouts = json.getJSONArray("layouts")
            for (i in 0 until layouts.length()) {
                val pages = layouts.getJSONObject(i).getJSONObject("layout").getJSONArray("pages")
                for (p in 0 until pages.length()) pages.getJSONObject(p).remove("type")
            }
        }
        return decodeHomeLayoutSet(json.toString())
    }

    private fun model(
        apps: Apps,
        sets: Sets,
    ) = LauncherShellViewModel(
        firstRunRepository = FirstRun(),
        installedAppRepository = apps,
        homeLayoutRepository = sets,
    )

    private fun LauncherShellViewModel.labels(): List<String> =
        state.value.homeLayout.pages.flatMap { it.items }.filterIsInstance<AppShortcutItem>().map { it.label }

    private fun allApps() = Apps((names + "Golf").map(::app))

    @Test
    fun aStoredLibraryLayoutOfEveryFormatIsConvertedOnceWithItsContent() {
        listOf(false, true).forEach { oldest ->
            val sets = Sets(storedLibrarySet(oldestFormat = oldest))

            val model = model(allApps(), sets)

            val layout = model.state.value.homeLayout
            assertEquals("oldest=$oldest", LauncherViewMode.STANDARD_APP_DRAWER, layout.viewMode)
            assertEquals(listOf("home", "library:1"), layout.pages.map { it.id.value })
            assertTrue(layout.pages.all { it.type == LauncherPageType.Home })
            assertEquals(names, model.labels())
            assertFalse(layout.settings.grid.compactLibraryPages)
            assertEquals(setOf(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)), sets.saved!!.layouts.keys)
        }
    }

    @Test
    fun aRemovedItemStaysRemovedAcrossARefreshAndARestart() {
        val apps = allApps()
        val sets = Sets(storedLibrarySet())
        val first = model(apps, sets)
        val bravo = first.state.value.homeLayout.pages.flatMap { it.items }.first { it.id.value.endsWith("bravo") }

        first.onHomeShortcutEdited(com.riffle.app.launcher.LauncherShellAction.RemoveHomeShortcut(bravo.id))
        runBlocking { first.refreshInstalledApps().join() }
        runBlocking { first.refreshInstalledApps().join() }

        val expected = names - "Bravo"
        assertEquals("after a refresh", expected, first.labels())
        val restarted = model(apps, sets)
        runBlocking { restarted.refreshInstalledApps().join() }
        assertEquals("after a restart", expected, restarted.labels())
        assertEquals(setOf(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)), sets.saved!!.layouts.keys)
    }

    @Test
    fun aRefreshNeverPlacesAnAppTheUserDidNotPlace() {
        val sets = Sets(storedLibrarySet())
        val model = model(allApps(), sets)

        runBlocking { model.refreshInstalledApps().join() }

        assertFalse("Golf is installed and not placed", "Golf" in model.labels())
        assertEquals(names, model.labels())
    }

    @Test
    fun aMovedItemStaysWhereItWasPutAcrossARefreshAndARestart() {
        val apps = allApps()
        val sets = Sets(storedLibrarySet())
        val first = model(apps, sets)
        val charlie = first.state.value.homeLayout.pages.flatMap { it.items }.first { it.id.value.endsWith("charlie") }

        first.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(itemId = charlie.id, cell = GridCell(column = 3, row = 1)),
        )
        runBlocking { first.refreshInstalledApps().join() }
        val restarted = model(apps, sets)
        runBlocking { restarted.refreshInstalledApps().join() }

        listOf(first, restarted).forEach { model ->
            val placement =
                model.state.value.homeLayout.pages.flatMap { it.items }
                    .first { it.label() == "Charlie" }.placement
            assertEquals(GridPlacement(GridCell(3, 1)), placement)
        }
    }

    @Test
    fun aNewInstallIsAppendedAfterTheLastItemAndNothingElseMoves() {
        val apps = allApps().also { it.apps = it.apps.filterNot { app -> app.label == "Golf" } }
        val sets = Sets(storedLibrarySet())
        val model = model(apps, sets)
        runBlocking { model.refreshInstalledApps().join() }
        val before = model.state.value.homeLayout.pages.map { page -> page.items.map { it.id to it.placement } }

        apps.apps = apps.apps + app("Golf")
        model.onPackageInstalled(AppPackageName("com.example.golf"), AppProfile.personal())
        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(names + "Golf", model.labels())
        val after = model.state.value.homeLayout.pages.map { page -> page.items.map { it.id to it.placement } }
        assertEquals(before[0], after[0])
        assertEquals(before[1], after[1].take(before[1].size))
    }

    @Test
    fun everyDeviceClassOfTheStoredSetIsConvertedAndNoneOfThemIsRefilled() {
        val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.FOLDABLE)
        val set =
            HomeLayoutSet(
                activeKey = key,
                layouts =
                    mapOf(
                        key to libraryLayout(),
                        foldableKey to libraryLayout(HomeLayoutDeviceClass.FOLDABLE, compact = false),
                    ),
                preferredModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to LauncherViewMode.HOME_SCREEN_LIBRARY,
                        HomeLayoutDeviceClass.FOLDABLE to LauncherViewMode.HOME_SCREEN_LIBRARY,
                    ),
            )
        val sets = Sets(decodeHomeLayoutSet(encodeHomeLayoutSet(set)))

        val model = model(allApps(), sets)
        model.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                HomeLayoutDeviceClass.FOLDABLE,
                setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )
        runBlocking { model.refreshInstalledApps().join() }

        assertTrue(sets.saved!!.layouts.keys.none { it.viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY })
        assertEquals(names, model.labels())
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, sets.saved!!.activeKey.deviceClass)
        assertEquals(
            names,
            sets.saved!!.layoutFor(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE))
                .pages.flatMap { it.items }.filterIsInstance<AppShortcutItem>().map { it.label },
        )
    }

    @Test
    fun simpleModeOnAndOffLeavesTheArrangementAndTheStoredSetUntouched() {
        val apps = allApps()
        val sets = Sets(storedLibrarySet())
        val model = model(apps, sets)
        runBlocking { model.refreshInstalledApps().join() }
        val setBefore = model.state.value.homeLayoutSet
        val savedBefore = sets.saved
        val writesBefore = sets.writes

        model.onLauncherSettingsActionSelected(LauncherShellAction.SetSimpleMode(true))
        runBlocking { model.refreshInstalledApps().join() }
        assertEquals(setBefore, model.state.value.homeLayoutSet)
        model.onLauncherSettingsActionSelected(LauncherShellAction.SetSimpleMode(false))
        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(setBefore, model.state.value.homeLayoutSet)
        assertEquals(savedBefore, sets.saved)
        assertEquals(writesBefore, sets.writes)
        assertEquals(names, model.labels())
    }

    @Test
    fun anAlreadyStandardHomeIsNotRewrittenByTheRetirement() {
        val sets = Sets(HomeLayoutSet.standard())
        val model = model(allApps(), sets)
        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(0, sets.writes)
        assertNull(sets.saved!!.layouts[HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY)])
        assertEquals(emptyList<String>(), model.labels())
    }

    private fun com.riffle.core.domain.launcher.home.LauncherItem.label(): String = (this as AppShortcutItem).label
}

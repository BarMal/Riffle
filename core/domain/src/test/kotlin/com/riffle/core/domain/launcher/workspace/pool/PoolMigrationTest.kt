package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.workspace.HomeLayoutWorkspaceMapper
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoolMigrationTest {
    private fun app(
        id: String,
        pkg: String,
        col: Int,
        row: Int,
    ) = AppShortcutItem(LauncherItemId(id), appIdentity(pkg), pkg, placement = at(col, row))

    private fun homePage(
        id: String,
        vararg items: LauncherItem,
        grid: GridDimensions = GridDimensions(4, 6),
    ) = LauncherPage(LauncherPageId(id), LauncherPageType.Home, grid, items.toList())

    private fun layout(
        mode: LauncherViewMode,
        vararg pages: LauncherPage,
    ) = HomeLayout(mode, pages.toList(), pages.first().id, DockModel(capacity = 5))

    /** The owner-shaped phone data: a Library home with apps, a folder and a widget, a second page, All apps. */
    private val libraryPhone =
        layout(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            homePage(
                "home",
                app("app:personal:com.a/com.a.Main:1", "com.a", 0, 0),
                app("app:personal:com.b/com.b.Main:1", "com.b", 1, 0),
                FolderItem(
                    LauncherItemId("folder-1"),
                    "Social",
                    listOf(
                        AppShortcutItem(LauncherItemId("app:personal:com.c/com.c.Main:1"), appIdentity("com.c"), "c"),
                        AppShortcutItem(LauncherItemId("app:personal:com.d/com.d.Main:1"), appIdentity("com.d"), "d"),
                    ),
                    at(2, 0),
                ),
                WidgetItem(
                    LauncherItemId("widget:42"),
                    HostedWidgetId(42),
                    "Clock",
                    WidgetResizeConstraints(minSpan = GridSpan(2, 1), maxSpan = GridSpan(4, 2)),
                    at(0, 1, 4, 2),
                ),
            ),
            homePage("second", app("app:personal:com.a/com.a.Main:2", "com.a", 3, 5)).copy(isPinned = true),
            LauncherPage(LauncherPageId("all"), LauncherPageType.AllApps, GridDimensions(4, 6)),
        )

    private val standardPhone =
        layout(
            LauncherViewMode.STANDARD_APP_DRAWER,
            homePage("home", app("app:personal:com.a/com.a.Main:1", "com.a", 0, 0)),
            LauncherPage(
                LauncherPageId("today"),
                LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                GridDimensions(4, 6),
                listOf(app("generated:today:1", "com.z", 0, 0)),
                generatedContentOverflowCount = 3,
            ),
        )

    private val foldableLibrary =
        layout(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            homePage("home", app("app:personal:com.f/com.f.Main:1", "com.f", 5, 4), grid = GridDimensions(6, 8)),
        )

    private val foldableKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.FOLDABLE)

    private val layoutSet =
        HomeLayoutSet(
            activeKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE),
            layouts =
                mapOf(
                    HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE) to libraryPhone,
                    HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE) to standardPhone,
                    foldableKey to foldableLibrary,
                ),
        )

    private fun homePagesOf(layout: HomeLayout) = layout.pages.filter { it.type == LauncherPageType.Home }

    @Test
    fun everyHomePageRoundTripsExactlyThroughTheArrangement() {
        val result = PoolMigration.migrate(layoutSet)
        assertEquals(emptyList(), result.issues)
        layoutSet.layouts.keys.forEach { key ->
            val pool = result.pools.getValue(key.deviceClass)
            val back = PoolMigration.homePages(pool, key.deviceClass, key.viewMode)
            assertEquals(homePagesOf(layoutSet.layoutFor(key)), back, "$key")
        }
    }

    @Test
    fun migrationIsDeterministicAndNeverTouchesTheLayoutSet() {
        val before = layoutSet.copy()
        val first = PoolMigration.migrate(layoutSet)
        assertEquals(first, PoolMigration.migrate(layoutSet))
        assertEquals(before, layoutSet)
        assertTrue(first.pools.values.all { PoolValidation.repair(it).isClean })
        first.pools.values.forEach(::assertInvariants)
    }

    @Test
    fun everyItemHasExactlyOneReferenceAndNothingIsSharedAcrossModes() {
        val phone = PoolMigration.migrate(layoutSet).pools.getValue(HomeLayoutDeviceClass.PHONE)
        val comA = phone.items.values.filterIsInstance<PoolApp>().filter { it.appIdentity.packageName.value == "com.a" }
        assertEquals(3, comA.size, "library home, library page 2 and standard home each own one")
        assertTrue(comA.all { PoolReferences.count(phone, it.id) == 1 })
        assertTrue(phone.items.keys.all { it.value.startsWith("pi:phone:") })
    }

    @Test
    fun generatedAndAllAppsPagesCarryNoPlacedItems() {
        val phone = PoolMigration.migrate(layoutSet).pools.getValue(HomeLayoutDeviceClass.PHONE)
        val standard =
            phone.arrangements.getValue(
                HomeLayoutWorkspaceMapper.workspaceId(
                    HomeLayoutDeviceClass.PHONE,
                    LauncherViewMode.STANDARD_APP_DRAWER,
                ),
            )
        assertEquals(listOf(LauncherPageId("home")), standard.pages.map { it.id })
        assertTrue(phone.items.values.none { it.label == "com.z" })
    }

    @Test
    fun widgetsKeepTheirHostIdAndAnInvalidIdBecomesAPlaceholderThatRoundTrips() {
        val unbound = WidgetItem(LauncherItemId("widget:0"), HostedWidgetId(0), "Gone", placement = at(0, 3, 2, 1))
        val set = HomeLayoutSet.fromLayout(layout(LauncherViewMode.STANDARD_APP_DRAWER, homePage("home", unbound)))
        val pool = PoolMigration.migrate(set).pools.getValue(HomeLayoutDeviceClass.PHONE)
        val widget = pool.items.values.single() as PoolWidget
        assertEquals(null, widget.hostedId)
        assertEquals(null, widget.provider)
        assertEquals(
            homePagesOf(set.activeLayout),
            PoolMigration.homePages(pool, HomeLayoutDeviceClass.PHONE, LauncherViewMode.STANDARD_APP_DRAWER),
        )
    }

    @Test
    fun malformedLegacyDataIsReportedNotCrashedOn() {
        val noCell = AppShortcutItem(LauncherItemId("nocell"), appIdentity("n"), "n")
        val dupA = app("dup", "d", 0, 0)
        val dupB = app("dup", "d2", 1, 0)
        val clash = app("clash", "c", 0, 0)
        val sharedHost = WidgetItem(LauncherItemId("w1"), HostedWidgetId(5), "w", placement = at(2, 2))
        val sharedHost2 = WidgetItem(LauncherItemId("w2"), HostedWidgetId(5), "w", placement = at(3, 3))
        val set =
            HomeLayoutSet.fromLayout(
                layout(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    homePage("home", noCell, dupA, dupB, clash, sharedHost, sharedHost2),
                ),
            )
        val result = PoolMigration.migrate(set)
        val kinds = result.issues.map { it.kind }.toSet()
        assertEquals(
            setOf(
                PoolIssueKind.UNPLACED_ITEM,
                PoolIssueKind.INVALID_GEOMETRY,
                PoolIssueKind.DUPLICATE_HOST_ID,
                PoolIssueKind.ORPHAN_ITEM,
            ),
            kinds,
        )
        result.pools.values.forEach(::assertInvariants)
        assertTrue(
            PoolItemId("pi:phone:standard_app_drawer:dup#2") in
                result.pools.getValue(HomeLayoutDeviceClass.PHONE).items,
        )
    }

    @Test
    fun randomLayoutsBuiltWithTheGridEngineRoundTrip() {
        val engine = GridPlacementEngine()
        for (seed in 1..40) {
            val rnd = Random(seed)
            var page = LauncherPage(LauncherPageId("home"), grid = GridDimensions(4, 5))
            repeat(rnd.nextInt(25)) { n ->
                val item: LauncherItem =
                    when (rnd.nextInt(4)) {
                        0 ->
                            FolderItem(
                                LauncherItemId("f$n"),
                                "F$n",
                                listOf(AppShortcutItem(LauncherItemId("c$n"), appIdentity("c$n"), "c")),
                            )
                        1 -> WidgetItem(LauncherItemId("w$n"), HostedWidgetId(100 + n), "W$n")
                        else -> AppShortcutItem(LauncherItemId("a$n"), appIdentity("p${rnd.nextInt(3)}"), "A$n")
                    }
                val span = if (item is WidgetItem) GridSpan(1 + rnd.nextInt(2), 1 + rnd.nextInt(2)) else GridSpan()
                (
                    engine.placeItemInFirstAvailableCell(
                        page,
                        item,
                        span,
                    ) as? PlaceLauncherItemResult.Placed
                )?.let { page = it.page }
            }
            val set = HomeLayoutSet.fromLayout(layout(LauncherViewMode.STANDARD_APP_DRAWER, page))
            val result = PoolMigration.migrate(set)
            assertEquals(emptyList(), result.issues, "seed $seed")
            val pool = result.pools.getValue(HomeLayoutDeviceClass.PHONE)
            assertEquals(
                listOf(page),
                PoolMigration.homePages(pool, HomeLayoutDeviceClass.PHONE, LauncherViewMode.STANDARD_APP_DRAWER),
                "seed $seed",
            )
        }
    }
}

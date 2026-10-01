package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
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
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Owner-shaped phone data: a Library home with apps, a folder and a widget, a second page and an All apps page. */
internal fun cutoverLayoutSet(): HomeLayoutSet {
    fun app(
        id: String,
        pkg: String,
        col: Int,
        row: Int,
    ) = AppShortcutItem(LauncherItemId(id), appIdentity(pkg), pkg, placement = at(col, row))

    fun home(
        id: String,
        vararg items: LauncherItem,
    ) = LauncherPage(LauncherPageId(id), LauncherPageType.Home, GridDimensions(4, 6), items.toList())

    val pages =
        listOf(
            home(
                "home",
                app("a1", "com.a", 0, 0),
                app("b1", "com.b", 1, 0),
                FolderItem(
                    LauncherItemId("folder-1"),
                    "Social",
                    listOf(AppShortcutItem(LauncherItemId("c1"), appIdentity("com.c"), "com.c")),
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
            home("second", app("a2", "com.a", 3, 5)),
            LauncherPage(LauncherPageId("all"), LauncherPageType.AllApps, GridDimensions(4, 6)),
        )
    val library = HomeLayout(LauncherViewMode.HOME_SCREEN_LIBRARY, pages, pages.first().id, DockModel(capacity = 5))
    val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    return HomeLayoutSet(activeKey = key, layouts = mapOf(key to library))
}

class PoolCutoverTest {
    private val layoutSet = cutoverLayoutSet()

    @Test
    fun firstRunMigratesAndSetsTheFlag() {
        val result = PoolCutover.ensureMigrated(null, layoutSet)
        assertTrue(result.changed)
        assertTrue(result.state.migrated)
        assertEquals(PoolMigration.migrate(layoutSet).pools, result.state.pools)
        assertEquals(emptyList(), result.issues)
        result.state.pools.values.forEach(::assertInvariants)
    }

    @Test
    fun runningTwiceIsIdempotentAndTheSecondRunWritesNothing() {
        val first = PoolCutover.ensureMigrated(null, layoutSet)
        val second = PoolCutover.ensureMigrated(first.state, layoutSet)
        assertEquals(first.state, second.state)
        assertTrue(!second.changed)
        assertSame(first.state, second.state)
    }

    @Test
    fun theFlagStopsAFurtherMigrationEvenWhenTheLayoutSetChanged() {
        val first = PoolCutover.ensureMigrated(null, layoutSet).state
        val other = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
        assertEquals(first, PoolCutover.ensureMigrated(first, other).state)
    }

    @Test
    fun theLayoutSetIsNeverChanged() {
        val before = layoutSet.copy()
        PoolCutover.ensureMigrated(null, layoutSet)
        assertEquals(before, layoutSet)
    }

    @Test
    fun aStoredNonEmptyPoolSurvivesAnUnflaggedStateAndOthersAreFilled() {
        val mine = emptyPool(listOf(W1)).add(poolApp("x"))
        val stored = PoolStoreState(migrated = false, pools = mapOf(HomeLayoutDeviceClass.PHONE to mine))
        val result = PoolCutover.ensureMigrated(stored, layoutSet)
        assertEquals(mine, result.state.pools.getValue(HomeLayoutDeviceClass.PHONE))
        assertTrue(result.state.migrated)
    }

    @Test
    fun widgetProvidersAreBackfilledWhereTheHostCanNameThem() {
        val result = PoolCutover.ensureMigrated(null, layoutSet) { id -> provider.takeIf { id == HostedWidgetId(42) } }
        val widget = result.state.pools.values.flatMap { it.items.values }.filterIsInstance<PoolWidget>().single()
        assertEquals(provider, widget.provider)
        assertEquals(HostedWidgetId(42), widget.hostedId)
    }

    @Test
    fun anUnresolvedProviderLeavesAPlaceholderWithTheHostId() {
        val result = PoolCutover.ensureMigrated(null, layoutSet) { null }
        val widget = result.state.pools.values.flatMap { it.items.values }.filterIsInstance<PoolWidget>().single()
        assertEquals(null, widget.provider)
        assertEquals(HostedWidgetId(42), widget.hostedId)
    }

    @Test
    fun backfillNeverOverwritesAKnownProviderOrTouchesPlaceholders() {
        val known = poolWidget("k", host = 7)
        val placeholder = poolWidget("p", host = null, withProvider = false)
        val pool = emptyPool().add(known).add(placeholder, cell = at(0, 2))
        val result = PoolWidgetBackfill.apply(pool) { error("must not be asked") }
        assertSame(pool, result)
    }
}

class PoolReimportTest {
    @Test
    fun reimportReplacesPoolsWithAFreshImportAndKeepsTheFlag() {
        val first = PoolCutover.ensureMigrated(null, cutoverLayoutSet()).state
        val result = PoolCutover.reimport(cutoverLayoutSet())
        assertTrue(result.state.migrated)
        assertTrue(result.changed)
        assertEquals(first.pools, result.state.pools)
    }

    @Test
    fun reimportBackfillsProvidersToo() {
        val result = PoolCutover.reimport(cutoverLayoutSet()) { provider }
        val widget = result.state.pools.values.flatMap { it.items.values }.filterIsInstance<PoolWidget>().single()
        assertEquals(provider, widget.provider)
    }
}

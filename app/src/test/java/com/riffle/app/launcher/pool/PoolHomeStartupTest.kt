package com.riffle.app.launcher.pool

import com.riffle.app.launcher.pool.PoolHomeFixture.homeItems
import com.riffle.app.launcher.pool.PoolHomeFixture.legacyId
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolPlacement
import com.riffle.core.domain.launcher.workspace.pool.PoolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolHomeStartupTest {
    private val legacy = PoolHomeFixture.legacySet()

    private fun open(
        layouts: DiskHomeLayoutStore,
        pools: DiskPoolStore,
        enabled: Boolean = true,
    ): Pair<HomeLayoutOpening, CachedPoolRepository> {
        val pool = CachedPoolRepository(pools)
        return runBlocking { openHomeLayouts(layouts, pool, enabled) } to pool
    }

    @Test
    fun theFirstCutoverImportsThePoolThenKeepsTheFullSetAsTheSnapshot() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        assertTrue(opening.poolHome)
        assertTrue("the stripped set is written with the snapshot", opening.stored!!.activeLayout.homeItems().isEmpty())
        assertEquals(opening.stored, layouts.stored)
        assertEquals(
            "the snapshot is the pool's projection: every item",
            legacy.activeLayout.homeItems().map { it.id.value },
            layouts.snapshot!!.activeLayout.homeItems().map { it.id.value.legacyId() },
        )
        assertNotNull(pools.stored)
        assertEquals(pools.stored, pool.current())
    }

    @Test
    fun thePoolProjectionAtStartupShowsTheLegacyItems() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val (opening, pool) = open(layouts, DiskPoolStore())
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool)
        repository.open(opening.stored)
        val shown = repository.loadHomeLayoutSet()!!.activeLayout
        assertEquals(
            legacy.activeLayout.homeItems().map { it.id.value },
            shown.homeItems().map { it.id.value.legacyId() },
        )
        assertEquals(legacy.activeLayout.pages.map { it.id }, shown.pages.map { it.id })
        assertEquals(
            "the stored set no longer holds placed items",
            emptyList<Any>(),
            layouts.stored!!.activeLayout.homeItems() + layouts.stored!!.activeLayout.dock.items,
        )
        assertEquals(
            "All-apps pages stay in HomeLayoutSet",
            legacy.activeLayout.pages.first { it.type == LauncherPageType.AllApps },
            layouts.stored!!.activeLayout.pages.first { it.type == LauncherPageType.AllApps },
        )
    }

    @Test
    fun aPoolThatCannotBeWrittenKeepsTheLegacyPathAndTouchesNothing() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val (opening, _) = open(layouts, DiskPoolStore(failWrite = true))
        assertFalse(opening.poolHome)
        assertNull(layouts.snapshot)
        assertEquals(legacy, layouts.stored)
    }

    @Test
    fun theLegacyFlagPathReadsAndWritesNoPool() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools, enabled = false)
        assertFalse(opening.poolHome)
        assertEquals(legacy, opening.stored)
        assertNull(pool.current())
        assertEquals(0, pools.writes)
        assertNull(layouts.snapshot)
    }

    @Test
    fun turningTheSwitchOffHandsTheItemsBackToHomeLayoutSet() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).open(opening.stored)
        assertTrue(layouts.stored!!.activeLayout.homeItems().isEmpty())

        val (handedBack, _) = open(layouts, pools, enabled = false)
        assertFalse(handedBack.poolHome)
        assertNull(layouts.snapshot)
        assertEquals(
            legacy.activeLayout.homeItems().map { it.id.value },
            layouts.stored!!.activeLayout.homeItems().map { it.id.value.legacyId() },
        )
        assertEquals(1, layouts.stored!!.activeLayout.dock.items.size)

        val (again, _) = open(layouts, pools)
        assertTrue("switching back on cuts over again", again.poolHome)
        assertNotNull(layouts.snapshot)
    }

    @Test
    fun aLostPoolIsReImportedFromTheSnapshotNotFromTheStrippedSet() {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).open(opening.stored)
        pools.stored = null

        val (reopened, fresh) = open(layouts, pools)
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), fresh)
        repository.open(reopened.stored)
        assertEquals(3, repository.loadHomeLayoutSet()!!.activeLayout.homeItems().size)
    }

    /** A cut-over store: the pool written, `HomeLayoutSet` stripped, a recent snapshot. */
    private fun cutOverStores(): Pair<DiskHomeLayoutStore, DiskPoolStore> {
        val layouts = DiskHomeLayoutStore(stored = legacy)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).open(opening.stored)
        assertTrue(layouts.stored!!.activeLayout.homeItems().isEmpty())
        return layouts to pools
    }

    @Test
    fun anUnreadableSnapshotNeverCutsOverAgainAndKeepsThePool() {
        val (layouts, pools) = cutOverStores()
        val before = pools.stored
        layouts.snapshotUnreadable = true
        val (opening, pool) = open(layouts, pools)
        assertTrue(opening.poolHome)
        assertTrue(layouts.snapshotQuarantined)
        assertEquals("the pool is not resynced from the stripped set", before, pools.stored)
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(opening.stored) }
        assertEquals(3, repository.loadHomeLayoutSet()!!.activeLayout.homeItems().size)
    }

    @Test
    fun anUnreadableSnapshotAndAnUnreadablePoolRunTheLegacyPathAndWriteNothing() {
        val (layouts, pools) = cutOverStores()
        layouts.snapshotUnreadable = true
        pools.failRead = true
        val writes = layouts.writes
        val (opening, _) = open(layouts, pools)
        assertFalse(opening.poolHome)
        assertEquals(writes, layouts.writes)
        assertTrue("the marker stays", layouts.snapshotUnreadable)
    }

    @Test
    fun aLostMarkerOverAStrippedSetKeepsThePool() {
        val (layouts, pools) = cutOverStores()
        val before = pools.stored
        layouts.snapshot = null
        val (opening, _) = open(layouts, pools)
        assertTrue(opening.poolHome)
        assertEquals("PoolCutover's hard guard: no resync from a set with no placed items", before, pools.stored)
        assertEquals(3, layouts.snapshot!!.activeLayout.homeItems().size)
    }

    @Test
    fun anUndecodablePoolIsQuarantinedAndRebuiltFromTheSnapshot() {
        val (layouts, pools) = cutOverStores()
        pools.corrupt = true
        val (opening, pool) = open(layouts, pools)
        assertTrue(opening.poolHome)
        assertTrue(pools.quarantined)
        assertNotNull("recovered and written once the original was copied away", pools.stored)
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(opening.stored) }
        assertEquals(3, repository.loadHomeLayoutSet()!!.activeLayout.homeItems().size)
    }

    @Test
    fun aFallbackSessionsAdditionsAreKeptAndThePoolStaysPreferred() {
        val (layouts, pools) = cutOverStores()
        // The pool is newer than the snapshot: an app the snapshot never saw.
        val newer = CachedPoolRepository(pools).also { runBlocking { it.initialize(layouts.snapshot!!) } }
        val ws = PoolHomeSync.workspaceOf(PoolHomeFixture.key)
        val phone = newer.pool(HomeLayoutDeviceClass.PHONE)!!
        val withNewer =
            PoolPlacement.addNew(
                phone,
                ws,
                PoolHomeFixture.home,
                PoolApp(PoolItemId("pool-newer"), PoolHomeFixture.identity("n"), "n"),
                PoolHomeFixture.at(3, 0),
            )
        newer.update(HomeLayoutDeviceClass.PHONE, (withNewer as PoolResult.Done).edit.pool)
        runBlocking { newer.flush() }

        pools.failRead = true
        val (fallback, _) = open(layouts, pools)
        assertFalse(fallback.poolHome)
        assertEquals("the full set is written back, never stripped", 3, layouts.stored!!.activeLayout.homeItems().size)
        assertNotNull("the marker stays", layouts.snapshot)
        // The fallback session adds an app and a widget (the legacy path writes full sets).
        val session = layouts.stored!!
        val widget =
            WidgetItem(
                LauncherItemId("widget-8"),
                HostedWidgetId(88),
                "Notes",
                placement = PoolHomeFixture.at(2, 3, 2, 1),
            )
        val added = listOf(PoolHomeFixture.app("added", 2), widget)
        layouts.stored =
            session.withActiveLayout(
                session.activeLayout.copy(
                    pages =
                        session.activeLayout.pages.map { page ->
                            if (page.type == LauncherPageType.Home) page.copy(items = page.items + added) else page
                        },
                ),
            )

        pools.failRead = false
        val (next, pool) = open(layouts, pools)
        assertTrue(next.poolHome)
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(next.stored) }
        val items = repository.loadHomeLayoutSet()!!.activeLayout.homeItems().map { it.id.value }
        assertTrue(items.toString(), "added" in items && "widget-8" in items && "pool-newer" in items)
        assertTrue(repository.isHostIdReferenced(HostedWidgetId(88)))
        runBlocking { repository.flushNow() }
        val (again, againPool) = open(layouts, pools)
        val restarted = poolHomeRepository(WriteThroughHomeLayouts(layouts), againPool).also { it.open(again.stored) }
        assertEquals(items, restarted.loadHomeLayoutSet()!!.activeLayout.homeItems().map { it.id.value })
    }

    @Test
    fun anUndecodablePoolAndAnUnreadableSnapshotMigrateNothingAndWriteNothing() {
        val (layouts, pools) = cutOverStores()
        pools.corrupt = true
        layouts.snapshotUnreadable = true
        val poolWrites = pools.writes
        val layoutWrites = layouts.writes
        val (opening, pool) = open(layouts, pools)
        assertFalse(opening.poolHome)
        assertTrue(opening.damaged)
        assertNull("no pool was derived from the stripped set", pool.current())
        assertEquals(poolWrites, pools.writes)
        assertTrue("the original pool blob is kept", pools.corrupt && pools.quarantined)
        assertEquals(layoutWrites, layouts.writes)
        assertTrue(layouts.snapshotUnreadable && layouts.snapshotQuarantined)
    }

    @Test
    fun aHomeLayoutStoreThatCannotBeReadRunsTheLegacyPathAndWritesNothing() {
        val layouts = DiskHomeLayoutStore(stored = legacy, failRead = true)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        assertFalse(opening.poolHome)
        assertFalse(opening.persist)
        assertTrue(opening.damaged)
        assertNull(opening.stored)
        assertNull(pool.current())
        assertEquals(0, pools.writes)
        assertEquals(0, layouts.writes)
    }

    @Test
    fun aCleanStartIsNotDamaged() {
        val (layouts, pools) = cutOverStores()
        assertFalse(open(layouts, pools).first.damaged)
    }
}

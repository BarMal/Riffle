package com.riffle.app.launcher.pool

import com.riffle.app.launcher.pool.PoolHomeFixture.homeItems
import com.riffle.app.launcher.pool.PoolHomeFixture.legacyId
import com.riffle.core.domain.launcher.home.LauncherPageType
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
    fun anUnreadableSnapshotAndAnUnreadablePoolShowTheHomeReadOnlyAndWriteNothing() {
        val (layouts, pools) = cutOverStores()
        layouts.snapshotUnreadable = true
        pools.failRead = true
        val writes = layouts.writes
        val (opening, _) = open(layouts, pools)
        assertFalse(opening.poolHome)
        assertTrue(opening.readOnly)
        assertFalse(opening.allowsHostIdDeletion)
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
    fun aPoolThatCannotBeReadShowsTheSnapshotReadOnlyAndTheNextStartUsesThePool() {
        val (layouts, pools) = cutOverStores()
        val stripped = layouts.stored
        pools.failRead = true
        val writes = layouts.writes
        val (opening, _) = open(layouts, pools)
        assertFalse(opening.poolHome)
        assertTrue(opening.readOnly)
        assertFalse("no host id may be deleted in this process", opening.allowsHostIdDeletion)
        assertEquals(layouts.snapshot, opening.stored)
        assertEquals("nothing written", writes, layouts.writes)
        assertEquals(stripped, layouts.stored)
        assertNotNull("the marker stays", layouts.snapshot)

        pools.failRead = false
        val (next, pool) = open(layouts, pools)
        assertTrue(next.poolHome)
        assertTrue(next.allowsHostIdDeletion)
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(next.stored) }
        assertEquals(3, repository.loadHomeLayoutSet()!!.activeLayout.homeItems().size)
    }

    @Test
    fun anUndecodableHomeLayoutSetIsQuarantinedNeverOverwrittenAndShownReadOnlyFromTheSnapshot() {
        val (layouts, pools) = cutOverStores()
        layouts.corrupt = true
        val writes = layouts.writes
        val poolWrites = pools.writes
        val (opening, _) = open(layouts, pools)
        assertTrue(opening.readOnly)
        assertTrue(opening.damaged)
        assertTrue(layouts.quarantined)
        assertEquals(layouts.snapshot, opening.stored)
        assertEquals(writes, layouts.writes)
        assertEquals(poolWrites, pools.writes)
    }

    @Test
    fun anUndecodableHomeLayoutSetWithoutASnapshotNeverCutsOverFromDefaults() {
        val layouts = DiskHomeLayoutStore(stored = legacy, corrupt = true)
        val pools = DiskPoolStore()
        val (opening, pool) = open(layouts, pools)
        assertTrue(opening.readOnly)
        assertNull("defaults, in memory only", opening.stored)
        assertTrue(layouts.quarantined)
        assertNull(layouts.snapshot)
        assertEquals(0, layouts.writes)
        assertEquals(0, pools.writes)
        assertNull(pool.current())
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
        assertTrue(opening.readOnly)
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
        assertTrue(opening.readOnly)
        assertFalse(opening.allowsHostIdDeletion)
        assertTrue(opening.damaged)
        assertNull(opening.stored)
        assertNull(pool.current())
        assertEquals(0, pools.writes)
        assertEquals(0, layouts.writes)
    }

    @Test
    fun aCleanStartIsNotDamagedEvenWithQuarantinedCopiesLeft() {
        val (layouts, pools) = cutOverStores()
        layouts.snapshotQuarantined = true
        pools.quarantined = true
        layouts.quarantined = true
        val opening = open(layouts, pools).first
        assertFalse(opening.damaged)
        assertTrue(opening.allowsHostIdDeletion)
    }

    @Test
    fun theRecoveryNoticeShowsOncePerIncidentAndResetsAfterACleanStart() {
        val first = recoveryNoticeStep(alreadyShown = false, damaged = true)
        assertTrue(first.show)
        val again = recoveryNoticeStep(alreadyShown = first.shownAfter, damaged = true)
        assertFalse("still damaged: not again", again.show)
        val clean = recoveryNoticeStep(alreadyShown = again.shownAfter, damaged = false)
        assertFalse(clean.show)
        assertTrue("a later incident is announced again", recoveryNoticeStep(clean.shownAfter, damaged = true).show)
    }
}

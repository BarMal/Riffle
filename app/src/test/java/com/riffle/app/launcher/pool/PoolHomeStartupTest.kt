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
        assertEquals(legacy, opening.stored)
        assertEquals(legacy, layouts.snapshot)
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
}

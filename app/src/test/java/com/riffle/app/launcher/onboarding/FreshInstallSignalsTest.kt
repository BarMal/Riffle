package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspaceStorePort
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.pool.DiskHomeLayoutStore
import com.riffle.app.launcher.pool.DiskPoolStore
import com.riffle.app.launcher.pool.PoolHomeFixture
import com.riffle.app.launcher.pool.openHomeLayouts
import com.riffle.app.launcher.workspace.WorkspaceBootstrap
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Fresh install" for onboarding: nothing in the home layout store and nothing in the workspace store at start. */
class FreshInstallSignalsTest {
    private class Store(
        val stored: WorkspaceSet?,
        val fail: Boolean = false,
    ) : WorkspaceStorePort {
        override suspend fun read(): WorkspaceSet? = if (fail) error("disk") else stored

        override suspend fun write(set: WorkspaceSet) = Unit
    }

    private fun foundStored(store: Store): Boolean? {
        val repository = CachedWorkspaceRepository(store, CoroutineScope(Dispatchers.Unconfined))
        assertNull(repository.foundStored)
        runBlocking { repository.initialize(WorkspaceBootstrap::seed) }
        return repository.foundStored
    }

    @Test
    fun anEmptyWorkspaceStoreIsTheOnlyFreshOne() {
        assertEquals(false, foundStored(Store(null)))
        assertEquals(true, foundStored(Store(WorkspaceBootstrap.seed(null))))
        // A read that failed is never taken for a fresh install.
        assertEquals(true, foundStored(Store(null, fail = true)))
    }

    @Test
    fun anEmptyHomeLayoutStoreIsFreshOnlyOnTheFirstStart() {
        val empty = DiskHomeLayoutStore()
        val pools = DiskPoolStore()
        val first = runBlocking { openHomeLayouts(empty, CachedPoolRepository(pools), enabled = true) }
        assertTrue(first.nothingStored)
        // The first start cut over and wrote the home: the next start is not fresh.
        val second = runBlocking { openHomeLayouts(empty, CachedPoolRepository(pools), enabled = true) }
        assertFalse(second.nothingStored)
    }

    @Test
    fun anExistingHomeIsNeverFresh() {
        val stored = DiskHomeLayoutStore(stored = PoolHomeFixture.legacySet())
        val opening = runBlocking { openHomeLayouts(stored, CachedPoolRepository(DiskPoolStore()), enabled = true) }
        assertFalse(opening.nothingStored)
        val unreadable = DiskHomeLayoutStore(failRead = true)
        val damaged = runBlocking { openHomeLayouts(unreadable, CachedPoolRepository(DiskPoolStore()), true) }
        assertFalse(damaged.nothingStored)
    }
}

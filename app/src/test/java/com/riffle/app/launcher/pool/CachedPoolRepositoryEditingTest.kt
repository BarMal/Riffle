package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository's editing surface: in-memory update, one atomic debounced write, never overwriting on read failure. */
class CachedPoolRepositoryEditingTest {
    private class FakeStore(
        var stored: PoolStoreState? = null,
        val failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : PoolStorePort {
        val writes = mutableListOf<PoolStoreState>()

        override suspend fun read(): PoolStoreState? {
            if (failRead) error("disk")
            return stored
        }

        override suspend fun write(state: PoolStoreState) {
            if (failWrite) error("full")
            writes += state
            stored = state
        }
    }

    private val phone = HomeLayoutDeviceClass.PHONE

    private fun layoutSet(): HomeLayoutSet {
        val identity = AppIdentity(AppPackageName("com.a"), AppActivityName("com.a.Main"))
        val at = GridPlacement(GridCell(0, 0), GridSpan(1, 1))
        val items = listOf(AppShortcutItem(LauncherItemId("app:a"), identity, "com.a", placement = at))
        val page = LauncherPage(LauncherPageId("home"), LauncherPageType.Home, GridDimensions(4, 6), items)
        val layout = HomeLayout(LauncherViewMode.HOME_SCREEN_LIBRARY, listOf(page), page.id, DockModel(capacity = 5))
        val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, phone)
        return HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout))
    }

    private fun edited(pool: PlacedItemPool) = pool.copy(items = emptyMap())

    @Test
    fun updateBeforeInitializeChangesNothing() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        assertFalse(repository.update(phone, PlacedItemPool()))
        assertTrue(runBlocking { repository.flush() })
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun updatePublishesAtOnceAndFlushWritesOnceThenNothingIsLeft() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        runBlocking { repository.initialize(layoutSet()) }
        val writes = store.writes.size
        val version = repository.version.value
        val next = edited(repository.pool(phone)!!)
        assertTrue(repository.update(phone, next))
        assertEquals(next, repository.pool(phone))
        assertEquals(version + 1, repository.version.value)
        assertEquals(writes, store.writes.size)
        assertTrue(runBlocking { repository.flush() })
        assertEquals(writes + 1, store.writes.size)
        assertEquals(next, store.stored!!.poolFor(phone))
        assertTrue(runBlocking { repository.flush() })
        assertEquals(writes + 1, store.writes.size)
    }

    @Test
    fun manyUpdatesBeforeAFlushAreOneWriteOfTheLatest() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        runBlocking { repository.initialize(layoutSet()) }
        val writes = store.writes.size
        val original = repository.pool(phone)!!
        repository.update(phone, edited(original))
        repository.update(phone, original)
        assertTrue(runBlocking { repository.flush() })
        assertEquals(writes + 1, store.writes.size)
        assertEquals(original, store.stored!!.poolFor(phone))
    }

    @Test
    fun aFailedWriteIsToleratedKeptPendingAndRetried() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        runBlocking { repository.initialize(layoutSet()) }
        val next = edited(repository.pool(phone)!!)
        repository.update(phone, next)
        store.failWrite = true
        assertFalse(runBlocking { repository.flush() })
        assertEquals(next, repository.pool(phone))
        assertNotEquals(next, store.stored!!.poolFor(phone))
        store.failWrite = false
        assertTrue(runBlocking { repository.flush() })
        assertEquals(next, store.stored!!.poolFor(phone))
    }

    @Test
    fun aReadFailureNeverLetsAnEditOverwriteTheStore() {
        val store = FakeStore(failRead = true)
        val repository = CachedPoolRepository(store)
        runBlocking { repository.initialize(layoutSet()) }
        repository.update(phone, edited(repository.pool(phone)!!))
        assertFalse(runBlocking { repository.flush() })
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun reimportReplacesEditsAndLeavesNothingPending() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        runBlocking { repository.initialize(layoutSet()) }
        val original = repository.pool(phone)!!
        repository.update(phone, edited(original))
        runBlocking { repository.reimport(layoutSet()) }
        assertEquals(original, repository.pool(phone))
        assertEquals(original, store.stored!!.poolFor(phone))
        val writes = store.writes.size
        assertTrue(runBlocking { repository.flush() })
        assertEquals(writes, store.writes.size)
    }
}

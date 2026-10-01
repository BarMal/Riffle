package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedWorkspaceRepositoryTest {
    private class FakeStore(var stored: WorkspaceSet? = null) : WorkspaceStorePort {
        val writes = mutableListOf<WorkspaceSet>()

        override suspend fun read(): WorkspaceSet? = stored

        override suspend fun write(set: WorkspaceSet) {
            writes += set
            stored = set
        }
    }

    private val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun loadIsNullUntilInitialized() {
        assertNull(CachedWorkspaceRepository(FakeStore(), scope).load())
    }

    @Test
    fun initializeMigratesWhenNothingIsStoredAndWritesNothing() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedWorkspaceRepository(store, scope)
            repository.initialize(layoutSet)
            assertEquals(WorkspaceMigration.migrate(layoutSet), repository.load())
            assertTrue(store.writes.isEmpty())
        }

    @Test
    fun initializeNeverOverwritesStoredWorkspaces() =
        runBlocking {
            val deviceClass = layoutSet.activeKey.deviceClass
            val stored =
                WorkspaceSet().update(deviceClass) { layout ->
                    layout.rename(layout.activeId, "Mine")
                }
            val store = FakeStore(stored)
            val repository = CachedWorkspaceRepository(store, scope)
            repository.initialize(layoutSet)
            assertEquals("Mine", repository.load()?.workspacesFor(deviceClass)?.active?.name)
            assertTrue(store.writes.isEmpty())
            assertEquals(stored, store.stored)
        }

    @Test
    fun initializeFillsOnlyTheGapsAroundStoredLayouts() =
        runBlocking {
            val other = HomeLayoutDeviceClass.entries.first { it != layoutSet.activeKey.deviceClass }
            val stored = WorkspaceSet().update(other) { it.rename(it.activeId, "Kept") }
            val repository = CachedWorkspaceRepository(FakeStore(stored), scope)
            repository.initialize(layoutSet)
            assertEquals("Kept", repository.load()?.workspacesFor(other)?.active?.name)
            assertNotNull(repository.load()?.layouts?.get(layoutSet.activeKey.deviceClass))
        }

    @Test
    fun saveUpdatesTheCopyAndPersists() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedWorkspaceRepository(store, scope)
            repository.initialize(layoutSet)
            val edited = repository.load()!!.update(layoutSet.activeKey.deviceClass) { it.rename(it.activeId, "New") }
            repository.save(edited)
            assertEquals(edited, repository.load())
            assertEquals(listOf(edited), store.writes)
        }

    @Test
    fun aSaveBeforeInitializeIsNotReplacedByTheLoad() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedWorkspaceRepository(store, scope)
            val early = WorkspaceSet().update(layoutSet.activeKey.deviceClass) { it.rename(it.activeId, "Early") }
            repository.save(early)
            repository.initialize(layoutSet)
            assertEquals(early, repository.load())
        }
}

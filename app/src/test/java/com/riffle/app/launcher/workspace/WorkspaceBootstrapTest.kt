package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspaceStorePort
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBootstrapTest {
    private class FakeStore(
        var stored: WorkspaceSet? = null,
        private val failure: Throwable? = null,
    ) : WorkspaceStorePort {
        val writes = mutableListOf<WorkspaceSet>()

        override suspend fun read(): WorkspaceSet? = failure?.let { throw it } ?: stored

        override suspend fun write(set: WorkspaceSet) {
            writes += set
            stored = set
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun seedingAnEmptyStoreGivesEveryDeviceClassADefault() {
        val seeded = WorkspaceBootstrap.seed(null)

        assertEquals(HomeLayoutDeviceClass.entries.toSet(), seeded.layouts.keys)
        seeded.layouts.values.forEach { assertTrue(it.workspaces.isNotEmpty()) }
    }

    @Test
    fun seedingKeepsStoredLayoutsExactlyAsTheyAre() {
        val stored = WorkspaceSet().update(phone) { it.rename(it.activeId, "Mine") }

        val seeded = WorkspaceBootstrap.seed(stored)

        assertEquals(stored.layouts.getValue(phone), seeded.layouts.getValue(phone))
        assertEquals("Mine", seeded.workspacesFor(phone).active.name)
        assertEquals(HomeLayoutDeviceClass.entries.toSet(), seeded.layouts.keys)
    }

    @Test
    fun initializingNeverOverwritesStoredWorkspacesAndWritesNothing() =
        runBlocking {
            val stored = WorkspaceSet().update(phone) { it.rename(it.activeId, "Mine") }
            val store = FakeStore(stored)
            val repository = CachedWorkspaceRepository(store, scope)

            repository.initialize(WorkspaceBootstrap::seed)

            assertEquals("Mine", repository.load()?.workspacesFor(phone)?.active?.name)
            assertTrue(store.writes.isEmpty())
            assertEquals(stored, store.stored)
        }

    @Test
    fun aStoreThatCannotBeReadFallsBackInMemoryAndNeverWritesOverIt() =
        runBlocking {
            val store = FakeStore(failure = java.io.IOException("disk"))
            val repository = CachedWorkspaceRepository(store, scope)

            repository.initialize(WorkspaceBootstrap::seed)
            val seeded = checkNotNull(repository.load())
            assertEquals(HomeLayoutDeviceClass.entries.toSet(), seeded.layouts.keys)

            repository.save(seeded.update(phone) { it.rename(it.activeId, "Edited") })

            assertEquals("Edited", repository.load()?.workspacesFor(phone)?.active?.name)
            assertTrue(store.writes.isEmpty())
        }

    @Test
    fun aStoredBlobThatDecodesToNothingFallsBackToTheDefaults() =
        runBlocking {
            val repository = CachedWorkspaceRepository(FakeStore(stored = null), scope)

            repository.initialize(WorkspaceBootstrap::seed)

            assertEquals(HomeLayoutDeviceClass.entries.toSet(), repository.load()?.layouts?.keys)
        }

    @Test
    fun versionChangesWhenTheCopyDoes() =
        runBlocking {
            val repository = CachedWorkspaceRepository(FakeStore(), scope)
            val before = repository.version.value

            repository.initialize(WorkspaceBootstrap::seed)
            val afterInit = repository.version.value
            repository.save(checkNotNull(repository.load()))

            assertTrue(afterInit > before)
            assertTrue(repository.version.value > afterInit)
        }
}

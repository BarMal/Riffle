package com.riffle.app.launcher.layouts

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.layouts.AppLayout
import com.riffle.core.domain.launcher.layouts.AppLayoutsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLayoutsRepositoryTest {
    private class MemoryStore(var stored: List<AppLayout> = emptyList()) : AppLayoutsStore {
        var writes = 0

        override fun read() = stored

        override fun write(layouts: List<AppLayout>) {
            writes++
            stored = layouts
        }
    }

    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private var now = 100L
    private var next = 0
    private val store = MemoryStore()
    private val repository = AppLayoutsRepository(store, { now }, { "id${next++}" })

    @Test
    fun addingWritesAndPutsTheNewLayoutFirst() {
        repository.add(app("notes"), app("maps"))
        now = 200
        repository.add(app("mail"), app("chat"))

        assertEquals(listOf("id1", "id0"), repository.layouts.value.map { it.id })
        assertEquals(store.stored, repository.layouts.value)
    }

    @Test
    fun anUnchangedEditWritesNothing() {
        repository.add(app("notes"), app("maps"))
        val writes = store.writes

        repository.add(app("notes"), app("maps"))
        repository.swap("nope")

        assertEquals(writes, store.writes)
    }

    @Test
    fun removeThenRestoreIsUndo() {
        repository.add(app("notes"), app("maps"))
        repository.add(app("mail"), app("chat"))
        val before = repository.layouts.value

        val removed = repository.remove("id0")
        repository.restore(removed.first { it.id == "id0" }, removed.indexOfFirst { it.id == "id0" })

        assertEquals(before, repository.layouts.value)
    }

    @Test
    fun theMigrationIsIdempotentAndKeepsWhatIsThere() {
        repository.add(app("mail"), app("chat"))
        val migrated = listOf(AppLayout("pool-1", app("notes"), app("maps")))

        repository.addMigrated(migrated)
        val once = repository.layouts.value
        repository.addMigrated(migrated)

        assertEquals(listOf("id0", "pool-1"), once.map { it.id })
        assertEquals(once, repository.layouts.value)
    }

    @Test
    fun layoutsAreReadFromTheStoreOnFirstUse() {
        store.stored = listOf(AppLayout("old", app("notes"), app("maps"), 5))

        repository.ensureLoaded()

        assertEquals(listOf("old"), repository.layouts.value.map { it.id })
    }

    @Test
    fun uninstalledAppsTakeTheirLayoutsAway() {
        repository.add(app("notes"), app("maps"))
        repository.add(app("mail"), app("chat"))

        repository.pruneUninstalled(setOf(app("mail"), app("chat"), app("notes")))

        assertEquals(listOf("id1"), repository.layouts.value.map { it.id })
    }
}

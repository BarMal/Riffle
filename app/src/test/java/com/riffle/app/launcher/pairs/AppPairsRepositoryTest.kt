package com.riffle.app.launcher.pairs

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.pairs.AppPairsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPairsRepositoryTest {
    private class MemoryStore(var stored: List<AppPair> = emptyList()) : AppPairsStore {
        var writes = 0

        override fun read() = stored

        override fun write(pairs: List<AppPair>) {
            writes++
            stored = pairs
        }
    }

    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private var now = 100L
    private var next = 0
    private val store = MemoryStore()
    private val repository = AppPairsRepository(store, { now }, { "id${next++}" })

    @Test
    fun addingWritesAndPutsTheNewPairFirst() {
        repository.add(app("notes"), app("maps"))
        now = 200
        repository.add(app("mail"), app("chat"))

        assertEquals(listOf("id1", "id0"), repository.pairs.value.map { it.id })
        assertEquals(store.stored, repository.pairs.value)
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
        val before = repository.pairs.value

        val removed = repository.remove("id0")
        repository.restore(removed.first { it.id == "id0" }, removed.indexOfFirst { it.id == "id0" })

        assertEquals(before, repository.pairs.value)
    }

    @Test
    fun theMigrationIsIdempotentAndKeepsWhatIsThere() {
        repository.add(app("mail"), app("chat"))
        val migrated = listOf(AppPair("pool-1", app("notes"), app("maps")))

        repository.addMigrated(migrated)
        val once = repository.pairs.value
        repository.addMigrated(migrated)

        assertEquals(listOf("id0", "pool-1"), once.map { it.id })
        assertEquals(once, repository.pairs.value)
    }

    @Test
    fun pairsAreReadFromTheStoreOnFirstUse() {
        store.stored = listOf(AppPair("old", app("notes"), app("maps"), 5))

        repository.ensureLoaded()

        assertEquals(listOf("old"), repository.pairs.value.map { it.id })
    }

    @Test
    fun uninstalledAppsTakeTheirPairsAway() {
        repository.add(app("notes"), app("maps"))
        repository.add(app("mail"), app("chat"))

        repository.pruneUninstalled(setOf(app("mail"), app("chat"), app("notes")))

        assertEquals(listOf("id1"), repository.pairs.value.map { it.id })
    }
}

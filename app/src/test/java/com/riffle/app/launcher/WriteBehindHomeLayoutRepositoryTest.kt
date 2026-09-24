package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WriteBehindHomeLayoutRepositoryTest {
    private val standard = HomeLayoutSet.standard()
    private val library = standard.selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
    private val cards = library.selectMode(LauncherViewMode.CARD_INTERFACE)

    @Test
    fun readsServeTheLoadedSetWithoutTouchingStorage() {
        val repository =
            WriteBehindHomeLayoutRepository(
                initialLayoutSet = library,
                persist = { error("nothing was saved") },
                scope = CoroutineScope(Job()),
            )

        assertEquals(library, repository.loadHomeLayoutSet())
        assertEquals(library.activeLayout, repository.loadHomeLayout())
        assertFalse(repository.hasPendingWrite)
    }

    @Test
    fun aBurstOfSavesIsReadBackAtOnceAndWrittenOnceAsTheNewest() {
        val persisted = mutableListOf<HomeLayoutSet>()

        runBlocking {
            val repository =
                WriteBehindHomeLayoutRepository(
                    initialLayoutSet = standard,
                    persist = { layoutSet -> persisted += layoutSet },
                    scope = this,
                    debounceMillis = 0,
                )

            repository.saveHomeLayoutSet(library)
            repository.saveHomeLayoutSet(standard)
            repository.saveHomeLayoutSet(cards)

            assertEquals(cards, repository.loadHomeLayoutSet())
        }

        assertEquals(listOf(cards), persisted)
    }

    @Test
    fun aSaveDuringASlowWriteIsWrittenAfterItNeverBefore() {
        val persisted = mutableListOf<HomeLayoutSet>()

        runBlocking {
            val repository =
                WriteBehindHomeLayoutRepository(
                    initialLayoutSet = null,
                    persist = { layoutSet ->
                        delay(SLOW_WRITE_MILLIS)
                        persisted += layoutSet
                    },
                    scope = this,
                    debounceMillis = 0,
                )

            repository.saveHomeLayoutSet(library)
            // Let the first write start and suspend mid-persist before the next edit arrives.
            yield()
            repository.saveHomeLayoutSet(cards)
        }

        assertEquals(listOf(library, cards), persisted)
    }

    @Test
    fun flushWritesImmediatelyWithoutWaitingForTheDebounce() {
        val persisted = mutableListOf<HomeLayoutSet>()

        runBlocking {
            val writerJob = Job()
            val repository =
                WriteBehindHomeLayoutRepository(
                    initialLayoutSet = standard,
                    persist = { layoutSet -> persisted += layoutSet },
                    scope = CoroutineScope(coroutineContext + writerJob),
                    debounceMillis = LONG_DEBOUNCE_MILLIS,
                )

            repository.saveHomeLayoutSet(library)
            assertTrue(repository.hasPendingWrite)
            repository.flush().join()

            assertEquals(listOf(library), persisted)
            assertFalse(repository.hasPendingWrite)
            writerJob.cancel()
        }
    }

    @Test
    fun aFailedWriteStaysPendingAndTheNextFlushRetriesIt() {
        val persisted = mutableListOf<HomeLayoutSet>()
        val failures = mutableListOf<IOException>()
        var failNextWrite = true

        runBlocking {
            val writerJob = Job()
            val repository =
                WriteBehindHomeLayoutRepository(
                    initialLayoutSet = standard,
                    persist = { layoutSet ->
                        if (failNextWrite) {
                            failNextWrite = false
                            throw IOException("disk full")
                        }
                        persisted += layoutSet
                    },
                    scope = CoroutineScope(coroutineContext + writerJob),
                    debounceMillis = LONG_DEBOUNCE_MILLIS,
                    onWriteFailed = { failure -> failures += failure },
                )

            repository.saveHomeLayoutSet(library)
            repository.flush().join()
            assertEquals(1, failures.size)
            assertTrue(repository.hasPendingWrite)

            repository.flush().join()
            writerJob.cancel()
        }

        assertEquals(listOf(library), persisted)
    }

    @Test
    fun savingOneLayoutReplacesOnlyTheActiveLayoutOfTheCurrentSet() {
        val repository =
            WriteBehindHomeLayoutRepository(
                initialLayoutSet = library,
                persist = {},
                scope = CoroutineScope(Job()),
                debounceMillis = LONG_DEBOUNCE_MILLIS,
            )
        val active = library.activeLayout
        val edited =
            active.copy(
                settings =
                    active.settings.copy(
                        grid = active.settings.grid.copy(dimensions = GridDimensions(columns = 3, rows = 3)),
                    ),
            )

        repository.saveHomeLayout(edited)

        assertEquals(library.withActiveLayout(edited), repository.loadHomeLayoutSet())
    }

    private companion object {
        const val SLOW_WRITE_MILLIS = 20L
        const val LONG_DEBOUNCE_MILLIS = 60_000L
    }
}

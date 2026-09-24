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
    fun theStopFlushReturnsOnlyOnceThePendingWriteHasLanded() {
        val persisted = mutableListOf<HomeLayoutSet>()
        val repository =
            WriteBehindHomeLayoutRepository(
                initialLayoutSet = standard,
                persist = { layoutSet ->
                    delay(SLOW_WRITE_MILLIS)
                    persisted += layoutSet
                },
                scope = CoroutineScope(Job()),
                // Far longer than the test: only the stop flush can write this edit.
                debounceMillis = NEVER_DEBOUNCE_MILLIS,
            )

        repository.saveHomeLayoutSet(cards)
        flushHomeLayoutBlocking(repository)

        // No join: the blocking flush itself guarantees the write is durable when it returns.
        assertEquals(listOf(cards), persisted)
        assertFalse(repository.hasPendingWrite)
    }

    @Test
    fun aStopFlushThatTimesOutLeavesTheEditPendingForTheNextWrite() {
        val repository =
            WriteBehindHomeLayoutRepository(
                initialLayoutSet = standard,
                persist = { delay(NEVER_DEBOUNCE_MILLIS) },
                scope = CoroutineScope(Job()),
                debounceMillis = NEVER_DEBOUNCE_MILLIS,
            )

        repository.saveHomeLayoutSet(cards)
        flushHomeLayoutBlocking(repository, timeoutMillis = SHORT_TIMEOUT_MILLIS)

        assertEquals(cards, repository.loadHomeLayoutSet())
        assertTrue(repository.hasPendingWrite)
    }

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
        const val SHORT_TIMEOUT_MILLIS = 50L
        const val NEVER_DEBOUNCE_MILLIS = 3_600_000L
        const val LONG_DEBOUNCE_MILLIS = 60_000L
    }
}

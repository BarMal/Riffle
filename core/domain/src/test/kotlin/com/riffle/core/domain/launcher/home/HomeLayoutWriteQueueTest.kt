package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLayoutWriteQueueTest {
    @Test
    fun startsFromTheLoadedSetWithNothingToWrite() {
        val loaded = HomeLayoutSet.standard()
        val queue = HomeLayoutWriteQueue(loaded)

        assertEquals(loaded, queue.current())
        assertFalse(queue.hasPendingWrite)
        assertNull(queue.pendingWrite())
    }

    @Test
    fun readsSeeEveryEditImmediatelyBeforeAnythingIsWritten() {
        val queue = HomeLayoutWriteQueue(HomeLayoutSet.standard())
        val library = HomeLayoutSet.standard().selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)

        queue.enqueue(library)

        assertEquals(library, queue.current())
        assertTrue(queue.hasPendingWrite)
    }

    @Test
    fun interleavedEditsCoalesceToTheNewestSetAndNeverLoseIt() {
        val queue = HomeLayoutWriteQueue(null)
        val first = HomeLayoutSet.standard()
        val second = first.selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val third = second.selectMode(LauncherViewMode.CARD_INTERFACE)
        val storage = mutableListOf<HomeLayoutSet>()

        val firstVersion = queue.enqueue(first)
        val secondVersion = queue.enqueue(second)
        // A write starts with the newest set so far...
        val inFlight = checkNotNull(queue.pendingWrite())
        // ...and a third edit lands while it is still being persisted.
        val thirdVersion = queue.enqueue(third)
        storage += inFlight.layoutSet
        queue.markWritten(inFlight.version)

        assertFalse(queue.isLatest(firstVersion))
        assertFalse(queue.isLatest(secondVersion))
        assertTrue(queue.isLatest(thirdVersion))
        assertEquals(second, inFlight.layoutSet)
        assertTrue(queue.hasPendingWrite)

        val followUp = checkNotNull(queue.pendingWrite())
        storage += followUp.layoutSet
        queue.markWritten(followUp.version)

        assertEquals(listOf(second, third), storage)
        assertEquals(third, storage.last())
        assertFalse(queue.hasPendingWrite)
        assertNull(queue.pendingWrite())
    }

    @Test
    fun aLateReportForAnOlderWriteNeverHidesANewerPendingSet() {
        val queue = HomeLayoutWriteQueue(null)
        val older = queue.enqueue(HomeLayoutSet.standard())
        val newerSet = HomeLayoutSet.standard().selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val newer = queue.enqueue(newerSet)

        queue.markWritten(newer)
        queue.markWritten(older)

        assertFalse(queue.hasPendingWrite)
        assertNull(queue.pendingWrite())
        assertEquals(newerSet, queue.current())
    }

    @Test
    fun aFailedWriteStaysPendingForTheNextAttempt() {
        val queue = HomeLayoutWriteQueue(null)
        val layoutSet = HomeLayoutSet.standard()
        queue.enqueue(layoutSet)

        // A writer that took the set but failed does not mark it written.
        checkNotNull(queue.pendingWrite())

        assertEquals(layoutSet, queue.pendingWrite()?.layoutSet)
    }
}

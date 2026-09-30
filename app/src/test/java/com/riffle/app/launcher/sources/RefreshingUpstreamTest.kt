package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class RefreshingUpstreamTest {
    private class QueueExecutor : Executor {
        val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private class ManualChanges : SourceChangeSource {
        var listener: (() -> Unit)? = null
        var stops = 0

        override fun observe(onChanged: () -> Unit): () -> Unit {
            listener = onChanged
            return { stops++ }
        }
    }

    @Test
    fun loadsOffTheCallerThreadOnConnectAndAfterEveryChange() {
        val executor = QueueExecutor()
        val changes = ManualChanges()
        var loads = 0
        val seen = mutableListOf<SourceState>()
        val upstream =
            RefreshingUpstream(executor, changes) {
                loads++
                SourceState.Ready(listOf(fakeItem("load$loads")))
            }

        upstream.connect { seen += it }
        assertEquals("nothing loads on the calling thread", 0, loads)
        executor.drain()
        changes.listener!!.invoke()
        executor.drain()

        assertEquals(2, loads)
        assertEquals(2, seen.size)
    }

    @Test
    fun coalescesBurstsOfChangesIntoOneReload() {
        val executor = QueueExecutor()
        val changes = ManualChanges()
        var loads = 0
        RefreshingUpstream(executor, changes) {
            loads++
            SourceState.Ready(emptyList())
        }.connect { }
        executor.drain()

        repeat(5) { changes.listener!!.invoke() }
        executor.drain()

        assertEquals(2, loads)
    }

    @Test
    fun cancelStopsObservingAndDropsQueuedWork() {
        val executor = QueueExecutor()
        val changes = ManualChanges()
        var loads = 0
        val seen = mutableListOf<SourceState>()
        val subscription =
            RefreshingUpstream(executor, changes) {
                loads++
                SourceState.Ready(emptyList())
            }.connect { seen += it }

        subscription.cancel()
        executor.drain()

        assertEquals(1, changes.stops)
        assertEquals(0, loads)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun failingLoadEmitsUnavailable() {
        val executor = QueueExecutor()
        val seen = mutableListOf<SourceState>()
        RefreshingUpstream(executor, SourceChangeSource.NONE) { error("boom") }.connect { seen += it }
        executor.drain()
        assertEquals(listOf<SourceState>(SourceState.Unavailable), seen)
    }

    @Test
    fun lambdaChangeSourcesAreLiveByDefault() {
        var stopped = false
        val changes = SourceChangeSource { { stopped = true } }

        assertTrue(changes.isLive)
        changes.observe { }.invoke()
        assertTrue(stopped)
    }

    @Test
    fun mergedChangeSourcesAreLiveOnlyWhenAnyIs() {
        assertEquals(false, SourceChangeSource.merge(listOf(SourceChangeSource.NONE)).isLive)
        assertEquals(true, SourceChangeSource.merge(listOf(SourceChangeSource.NONE, ManualChanges())).isLive)

        val first = ManualChanges()
        val second = ManualChanges()
        SourceChangeSource.merge(listOf(first, second)).observe { }.invoke()
        assertEquals(1, first.stops)
        assertEquals(1, second.stops)
    }
}

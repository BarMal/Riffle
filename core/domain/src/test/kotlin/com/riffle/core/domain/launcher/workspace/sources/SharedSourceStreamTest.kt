package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedSourceStreamTest {
    private val descriptor = SourceDescriptor(SourceId("fake"), setOf(SourceCapability.LIVE))

    private class Upstream {
        var connects = 0
        var cancels = 0
        var sink: SourceSink? = null

        fun connect(sink: SourceSink): SourceSubscription {
            connects++
            this.sink = sink
            return SourceSubscription { cancels++ }
        }
    }

    private class ManualScheduler : DelayedTaskScheduler {
        var pending: Runnable? = null
        var cancelled = 0

        override fun schedule(
            delayMillis: Long,
            task: Runnable,
        ): SourceSubscription {
            pending = task
            return SourceSubscription {
                cancelled++
                if (pending === task) pending = null
            }
        }

        fun fire() {
            pending?.also { pending = null }?.run()
        }
    }

    @Test
    fun `starts upstream once on first observer and stops on last`() {
        val upstream = Upstream()
        val stream = SharedSourceStream(descriptor, connect = upstream::connect)
        assertEquals(0, upstream.connects)

        val first = stream.subscribe { }
        val second = stream.subscribe { }
        assertEquals(1, upstream.connects)

        first.cancel()
        assertEquals(0, upstream.cancels)
        second.cancel()
        assertEquals(1, upstream.cancels)
    }

    @Test
    fun `new observer gets loading then replays latest state`() {
        val upstream = Upstream()
        val stream = SharedSourceStream(descriptor, connect = upstream::connect)
        val early = mutableListOf<SourceState>()
        stream.subscribe { early += it }
        assertEquals(listOf<SourceState>(SourceState.Loading), early)

        val ready = SourceState.Ready(listOf(fakeItem("a")))
        upstream.sink!!.emit(ready)
        val late = mutableListOf<SourceState>()
        stream.subscribe { late += it }

        assertEquals(listOf(SourceState.Loading, ready), early)
        assertEquals(listOf<SourceState>(ready), late)
    }

    @Test
    fun `synchronous emission during connect reaches the first observer once`() {
        val ready = SourceState.Ready(listOf(fakeItem("a")))
        val stream =
            SharedSourceStream(descriptor) { sink ->
                sink.emit(ready)
                SourceSubscription { }
            }
        val seen = mutableListOf<SourceState>()
        stream.subscribe { seen += it }
        assertEquals(listOf(SourceState.Loading, ready), seen)
    }

    @Test
    fun `cancelled observer receives nothing further and cancel is idempotent`() {
        val upstream = Upstream()
        val stream = SharedSourceStream(descriptor, connect = upstream::connect)
        val seen = mutableListOf<SourceState>()
        val sub = stream.subscribe { seen += it }
        sub.cancel()
        sub.cancel()
        upstream.sink!!.emit(SourceState.Unavailable)
        assertEquals(listOf<SourceState>(SourceState.Loading), seen)
        assertEquals(1, upstream.cancels)
    }

    @Test
    fun `restart after full stop connects again and resets to loading`() {
        val upstream = Upstream()
        val stream = SharedSourceStream(descriptor, connect = upstream::connect)
        stream.subscribe { }.also {
            upstream.sink!!.emit(SourceState.Unavailable)
            it.cancel()
        }
        val staleSink = upstream.sink!!

        val seen = mutableListOf<SourceState>()
        stream.subscribe { seen += it }
        staleSink.emit(SourceState.PermissionRequired)

        assertEquals(2, upstream.connects)
        assertEquals(listOf<SourceState>(SourceState.Loading), seen)
    }

    @Test
    fun `failing connect reports unavailable instead of throwing`() {
        val stream = SharedSourceStream(descriptor) { error("no upstream") }
        val seen = mutableListOf<SourceState>()
        stream.subscribe { seen += it }
        assertEquals(listOf(SourceState.Loading, SourceState.Unavailable), seen)
    }

    @Test
    fun `grace keeps upstream alive for a quick resubscribe`() {
        val upstream = Upstream()
        val scheduler = ManualScheduler()
        val stream = SharedSourceStream(descriptor, 500L, scheduler, upstream::connect)

        stream.subscribe { }.cancel()
        assertEquals(0, upstream.cancels)
        val seen = mutableListOf<SourceState>()
        upstream.sink!!.emit(SourceState.Unavailable)
        stream.subscribe { seen += it }
        assertEquals(1, scheduler.cancelled)
        scheduler.fire()

        assertEquals(1, upstream.connects)
        assertEquals(0, upstream.cancels)
        assertEquals(listOf<SourceState>(SourceState.Unavailable), seen)
    }

    @Test
    fun `grace expiry stops the upstream`() {
        val upstream = Upstream()
        val scheduler = ManualScheduler()
        val stream = SharedSourceStream(descriptor, 500L, scheduler, upstream::connect)
        stream.subscribe { }.cancel()
        scheduler.fire()
        assertEquals(1, upstream.cancels)
    }

    @Test
    fun `grace needs a scheduler`() {
        assertFailsWith<IllegalArgumentException> {
            SharedSourceStream(descriptor, graceMillis = 10L) { SourceSubscription { } }
        }
    }

    @Test
    fun `concurrent subscribe and cancel keeps one upstream and balanced attach detach`() {
        val connects = AtomicInteger()
        val cancels = AtomicInteger()
        val stream =
            SharedSourceStream(descriptor) {
                connects.incrementAndGet()
                SourceSubscription { cancels.incrementAndGet() }
            }
        val pool = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREADS)
        repeat(THREADS) {
            pool.execute {
                start.await()
                repeat(ROUNDS) { stream.subscribe { }.cancel() }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(connects.get(), cancels.get())
        val probe = stream.subscribe { }
        assertEquals(connects.get(), cancels.get() + 1)
        probe.cancel()
        assertEquals(connects.get(), cancels.get())
    }

    @Test
    fun `emissions arrive in order without duplicates`() {
        val upstream = Upstream()
        val stream = SharedSourceStream(descriptor, connect = upstream::connect)
        val seen = mutableListOf<SourceState>()
        stream.subscribe { state -> synchronized(seen) { seen += state } }
        val states = (1..COUNT).map { SourceState.Ready(listOf(fakeItem("i$it"))) }
        states.forEach { upstream.sink!!.emit(it) }
        assertEquals(listOf<SourceState>(SourceState.Loading) + states, seen)
    }

    private companion object {
        const val THREADS = 8
        const val ROUNDS = 500
        const val COUNT = 50
        const val TIMEOUT_SECONDS = 30L
    }
}

package com.riffle.app.launcher.stream

import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.stream.NextAlarm
import com.riffle.core.domain.launcher.stream.TypedSourceState
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNextAlarmTest {
    private val queue = ArrayDeque<Runnable>()
    private var changed: (() -> Unit)? = null
    private var stops = 0
    private var reads = 0
    private var alarm: NextAlarm? = NextAlarm("next", 5_000)
    private val changes =
        SourceChangeSource { onChanged ->
            changed = onChanged
            return@SourceChangeSource { stops++ }
        }
    private val provider = refreshingAlarmProvider({ queue.addLast(it) }, changes) { reads++.let { alarm } }

    private fun drain() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    @Test
    fun readsOffTheCallerThread_shareOneUpstream_andFollowChanges() {
        val a = mutableListOf<TypedSourceState<NextAlarm>>()
        val b = mutableListOf<TypedSourceState<NextAlarm>>()
        val first = provider.subscribe { a += it }
        provider.subscribe { b += it }
        assertEquals("nothing is read on the calling thread", 0, reads)
        drain()
        assertEquals(1, reads)
        assertEquals(TypedSourceState.Ready(listOf(NextAlarm("next", 5_000))), a.last())

        alarm = null
        changed!!()
        changed!!()
        drain()
        assertEquals("bursts coalesce", 2, reads)
        assertEquals(TypedSourceState.Ready(emptyList<NextAlarm>()), b.last())

        first.cancel()
        assertEquals(0, stops)
    }

    @Test
    fun aPlatformFailureReadsAsNoAlarm() {
        val failing = refreshingAlarmProvider({ it.run() }, SourceChangeSource.NONE) { error("no clock") }
        var state: TypedSourceState<NextAlarm>? = null
        failing.subscribe { state = it }
        assertEquals(TypedSourceState.Ready(emptyList<NextAlarm>()), state)
    }
}

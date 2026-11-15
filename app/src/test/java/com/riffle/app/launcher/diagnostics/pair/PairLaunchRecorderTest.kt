package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairLaunchRecorderTest {
    private var now = 10_000L
    private val job = Job()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    private val waited = mutableListOf<Long>()
    private val full = PairWindowState(false, "[0,0][1584,1856] 1584x1856 px")
    private val half = PairWindowState(true, "[0,0][792,1856] 792x1856 px")
    private var trace = emptyList<PairObservation>()
    private var open = false

    private val recorder =
        PairLaunchRecorder(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            clock = { now },
            windowMillis = 3_000,
            pause = {
                waited += it
                CompletableDeferred<Unit>().also { gate -> gates += gate }.await()
            },
        )

    @After
    fun tearDown() = job.cancel()

    private fun begin() {
        now = 10_000L
        recorder.begin(now) { events, isOpen ->
            trace = events
            open = isOpen
        }
    }

    private fun at(
        offset: Long,
        name: String,
        state: PairWindowState,
    ) {
        now = 10_000L + offset
        recorder.onSignal(name, state)
    }

    @Test
    fun nothingIsRecordedAndNoTimerRunsWithoutALaunch() {
        at(5, "onPause", full)
        assertTrue(trace.isEmpty())
        assertTrue(gates.isEmpty())
    }

    @Test
    fun theBaselineIsTheLastStateTheActivityReported() {
        at(-50, "onResume", full)
        begin()
        assertEquals(
            listOf(PairObservation(0, "launcher window at launch: multi-window=false, bounds=${full.bounds}")),
            trace,
        )
        assertTrue(open)
        assertEquals(listOf(3_000L), waited)
    }

    @Test
    fun lifecycleOrderAndOffsetsAreKept() {
        at(-1, "onResume", full)
        begin()
        at(40, "onPause", full)
        at(310, "onStop", full)
        at(900, "onStart", full)
        at(910, "onResume", full)
        assertEquals(
            listOf("onPause" to 40L, "onStop" to 310L, "onStart" to 900L, "onResume" to 910L),
            trace.drop(1).map { it.text to it.offsetMillis },
        )
    }

    @Test
    fun multiWindowAndBoundsTransitionsAreWrittenWhereTheyAreSeen() {
        at(-1, "onResume", full)
        begin()
        at(120, "onMultiWindowModeChanged(true)", half)
        val texts = trace.drop(1).map { it.text }
        // The callback names the change itself, so only the bounds change is added.
        assertEquals(listOf("onMultiWindowModeChanged(true)", "window bounds ${full.bounds} -> ${half.bounds}"), texts)
        at(130, "onConfigurationChanged", half)
        assertEquals("onConfigurationChanged", trace.last().text)
    }

    @Test
    fun aMultiWindowFlipSeenAtAnotherCallbackIsStillReported() {
        at(-1, "onResume", full)
        begin()
        at(60, "onResume", half.copy(bounds = full.bounds))
        assertTrue(trace.any { it.text == "isInMultiWindowMode false -> true" })
        assertFalse(trace.any { it.text.startsWith("window bounds") })
    }

    @Test
    fun theWindowEndsAfterItsTimeAndSaysWhereTheLauncherWasLeft() {
        at(-1, "onResume", full)
        begin()
        at(100, "onPause", full)
        now = 13_000
        gates.single().complete(Unit)
        assertFalse(open)
        assertEquals(3_000L, trace.last().offsetMillis)
        assertTrue(trace.last().text, trace.last().text.startsWith("observation ended (window over)"))
        assertTrue(trace.last().text.contains(full.bounds!!))
        val size = trace.size
        at(3_500, "onStop", full)
        assertEquals(size, trace.size)
    }

    @Test
    fun aNewLaunchClosesTheOldWatchAndCancelsItsTimer() {
        begin()
        val firstGate = gates.single()
        var secondOpen: Boolean? = null
        recorder.begin(10_000L) { _, isOpen -> secondOpen = isOpen }
        assertEquals(2, gates.size)
        assertFalse(open)
        assertTrue(trace.last().text, trace.last().text.startsWith("observation ended (superseded by a newer launch)"))
        assertEquals(true, secondOpen)
        firstGate.complete(Unit)
        assertEquals("the old timer must not close the new watch", true, secondOpen)
    }

    @Test
    fun theTraceIsBounded() {
        begin()
        repeat(200) { at(it.toLong(), "onConfigurationChanged", full) }
        assertEquals(60, trace.size)
    }
}

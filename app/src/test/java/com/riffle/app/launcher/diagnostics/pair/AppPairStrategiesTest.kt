package com.riffle.app.launcher.diagnostics.pair

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPairStrategiesTest {
    private val a = PairTarget("com.a", "com.a.Main", "A")
    private val b = PairTarget("com.b", "com.b.Main", "B")

    /** Records every call in order, with the pauses of the strategy interleaved; can fail chosen calls. */
    private class FakeStarter(
        val events: MutableList<String>,
        private val failOn: Set<String> = emptySet(),
    ) : PairStarter {
        override fun start(start: PairStart) {
            events += "start ${start.target.packageName} ${start.flags}"
            if (start.target.packageName in failOn) throw SecurityException("blocked ${start.target.packageName}")
        }

        override fun startAll(starts: List<PairStart>) {
            events += "startAll " + starts.joinToString { "${it.target.packageName} ${it.flags}" }
            if ("all" in failOn) throw UnsupportedOperationException("no")
        }

        override fun startTrampoline(request: TrampolineRequest) {
            events += "trampoline ${request.first.packageName} ${request.second.packageName} ${request.adjacentFirst}"
            if ("trampoline" in failOn) throw java.io.IOException("no activity")
        }
    }

    private fun run(
        method: PairMethod,
        delayMillis: Long = 600,
        failOn: Set<String> = emptySet(),
    ): Pair<List<String>, List<StartOutcome>> {
        val events = mutableListOf<String>()
        val outcomes =
            runBlocking {
                pairStrategyFor(method) { events += "pause $it" }.launch(a, b, FakeStarter(events, failOn), delayMillis)
            }
        return events to outcomes
    }

    private val adjacent = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    private val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
    private val multiple = Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    @Test
    fun sequentialStartsTheFirstNormallyWaitsThenStartsTheSecondAdjacent() {
        val (events, outcomes) = run(PairMethod.SEQUENTIAL, delayMillis = 750)
        assertEquals(
            listOf("start com.a $newTask", "pause 750", "start com.b ${adjacent or newTask or multiple}"),
            events,
        )
        assertTrue(outcomes.all { it.ok })
        assertEquals(2, outcomes.size)
    }

    @Test
    fun sequentialWithNoDelayDoesNotPause() {
        val (events, _) = run(PairMethod.SEQUENTIAL, delayMillis = 0)
        assertEquals(2, events.size)
        assertTrue(events.none { it.startsWith("pause") })
    }

    @Test
    fun sequentialRecordsAFailingFirstStartAndStillTriesTheSecond() {
        val (events, outcomes) = run(PairMethod.SEQUENTIAL, failOn = setOf("com.a"))
        assertEquals(3, events.size)
        assertFalse(outcomes[0].ok)
        assertEquals("SecurityException: blocked com.a", outcomes[0].error)
        assertTrue(outcomes[1].ok)
    }

    @Test
    fun sequentialRecordsAFailingSecondStart() {
        val (_, outcomes) = run(PairMethod.SEQUENTIAL, failOn = setOf("com.b"))
        assertTrue(outcomes[0].ok)
        assertEquals("SecurityException: blocked com.b", outcomes[1].error)
    }

    @Test
    fun bothAdjacentStartsBothWithTheAdjacentFlagInOrder() {
        val (events, outcomes) = run(PairMethod.BOTH_ADJACENT)
        assertEquals(
            listOf("start com.a ${adjacent or newTask}", "start com.b ${adjacent or newTask or multiple}"),
            events,
        )
        assertEquals(2, outcomes.size)
    }

    @Test
    fun bothAdjacentRecordsAFailureAndKeepsGoing() {
        val (events, outcomes) = run(PairMethod.BOTH_ADJACENT, failOn = setOf("com.a"))
        assertEquals(2, events.size)
        assertFalse(outcomes[0].ok)
        assertTrue(outcomes[1].ok)
    }

    @Test
    fun startActivitiesArrayIsOneCallWithTheSecondAdjacent() {
        val (events, outcomes) = run(PairMethod.START_ACTIVITIES_ARRAY)
        assertEquals(listOf("startAll com.a $newTask, com.b ${adjacent or newTask or multiple}"), events)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun startActivitiesArrayRecordsItsException() {
        val (_, outcomes) = run(PairMethod.START_ACTIVITIES_ARRAY, failOn = setOf("all"))
        assertEquals("UnsupportedOperationException: no", outcomes.single().error)
    }

    @Test
    fun trampolineMethodsStartOnlyTheTrampolineWithTheOrderRequested() {
        assertEquals(
            listOf("trampoline com.a com.b true"),
            run(PairMethod.TRAMPOLINE_ADJACENT_FIRST).first,
        )
        assertEquals(
            listOf("trampoline com.a com.b false"),
            run(PairMethod.TRAMPOLINE_ADJACENT_SECOND).first,
        )
    }

    @Test
    fun trampolineStartFailureIsRecorded() {
        val (_, outcomes) = run(PairMethod.TRAMPOLINE_ADJACENT_FIRST, failOn = setOf("trampoline"))
        assertEquals("IOException: no activity", outcomes.single().error)
    }

    @Test
    fun theTrampolineStepsPutTheAdjacentFlagOnTheRightApp() {
        val adjacentFirst = trampolineSteps(TrampolineRequest(a, b, adjacentFirst = true))
        assertEquals(listOf(a to (adjacent or newTask), b to newTask), adjacentFirst.map { it.target to it.flags })
        val adjacentSecond = trampolineSteps(TrampolineRequest(a, b, adjacentFirst = false))
        assertEquals(listOf(a to newTask, b to (adjacent or newTask)), adjacentSecond.map { it.target to it.flags })
    }

    @Test
    fun everyMethodHasAStrategyOfItsOwn() {
        PairMethod.entries.forEach { assertEquals(it, pairStrategyFor(it).method) }
    }

    @Test
    fun flagsAreDescribedByName() {
        assertEquals("LAUNCH_ADJACENT|NEW_TASK|MULTIPLE_TASK", PairFlags.describe(PairFlags.ADJACENT_NEW_MULTIPLE_TASK))
        assertEquals("NEW_TASK", PairFlags.describe(PairFlags.NEW_TASK))
    }
}

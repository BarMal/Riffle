package com.riffle.app.launcher.diagnostics.pair

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPairTest {
    private val notes = PairTarget("com.example.notes", "com.example.notes.Main", "Notes")
    private val maps = PairTarget("com.example.maps", "com.example.maps.Main", "Maps")
    private val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
    private val adjacent = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    private val multiple = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    private val clear = Intent.FLAG_ACTIVITY_CLEAR_TASK
    private val noAnim = Intent.FLAG_ACTIVITY_NO_ANIMATION

    private class Recorder : PairStarter {
        val events = mutableListOf<String>()

        override fun start(start: PairStart) {
            events += "start ${start.target.packageName} ${start.flags}"
        }

        override fun startAll(starts: List<PairStart>) {
            events += "startAll " + starts.joinToString { "${it.target.packageName} ${it.flags}" }
        }

        override fun startTrampoline(request: TrampolineRequest) {
            events += "trampoline"
        }
    }

    @Test
    fun newTaskIsAlwaysOnAndEverySwitchAddsExactlyItsFlag() {
        assertEquals(newTask, CustomAppFlags().flags)
        val expected =
            mapOf(
                CustomSwitch.ADJACENT to adjacent,
                CustomSwitch.MULTIPLE_TASK to multiple,
                CustomSwitch.CLEAR_TASK to clear,
                CustomSwitch.NO_ANIMATION to noAnim,
            )
        CustomSwitch.entries.forEach { switch ->
            val on = CustomAppFlags().with(switch, true)
            assertTrue(switch.name, on.isOn(switch))
            assertEquals(switch.name, newTask or expected.getValue(switch), on.flags)
            assertEquals(switch.name, CustomAppFlags(), on.with(switch, false))
            CustomSwitch.entries.filter { it != switch }.forEach { other -> assertFalse(on.isOn(other)) }
        }
    }

    @Test
    fun theSwitchesOfTheFirstAndSecondAppAreIndependent() {
        val pair = CustomPair(first = CustomAppFlags(adjacent = true), second = CustomAppFlags(clearTask = true))
        val (a, b) = pair.starts(notes, maps)
        assertEquals(PairStart(notes, newTask or adjacent), a)
        assertEquals(PairStart(maps, newTask or clear), b)
    }

    @Test
    fun flagsAreDescribedInAFixedOrderIncludingTheNewOnes() {
        val all = CustomAppFlags(true, true, true, true)
        assertEquals("LAUNCH_ADJACENT|NEW_TASK|MULTIPLE_TASK|CLEAR_TASK|NO_ANIMATION", all.description)
        assertEquals("NEW_TASK", CustomAppFlags().description)
        assertEquals("NEW_TASK|CLEAR_TASK", CustomAppFlags(clearTask = true).description)
        assertEquals(
            "LAUNCH_ADJACENT|NEW_TASK|NO_ANIMATION",
            CustomAppFlags(adjacent = true, noAnimation = true).description,
        )
    }

    @Test
    fun theSixPresetsFillTheSwitchesAsDescribed() {
        fun d(p: PairPreset) = p.pair.first.description to p.pair.second.description
        val both = "LAUNCH_ADJACENT|NEW_TASK"
        val bothMultiple = "LAUNCH_ADJACENT|NEW_TASK|MULTIPLE_TASK"
        val bothClear = "LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK"
        assertEquals(bothMultiple to bothMultiple, d(PairPreset.FRESH_BOTH))
        assertEquals(both to both, d(PairPreset.REUSE_BOTH))
        assertEquals(bothClear to bothClear, d(PairPreset.RESET_BOTH))
        assertEquals(both to bothMultiple, d(PairPreset.REUSE_FIRST_FRESH_SECOND))
        assertEquals(bothMultiple to both, d(PairPreset.FRESH_FIRST_REUSE_SECOND))
        assertEquals("NEW_TASK|CLEAR_TASK" to bothClear, d(PairPreset.ONE_CALL_RESET_BOTH))
        assertEquals(setOf(PairPreset.ONE_CALL_RESET_BOTH), PairPreset.entries.filter { it.pair.oneCall }.toSet())
    }

    @Test
    fun theDefaultIsTodaysBothAdjacentAndPresetsAreTheSameValuesAsTheirRecipes() {
        assertEquals(PairPreset.REUSE_FIRST_FRESH_SECOND.pair, CustomPair.DEFAULT)
        assertEquals(
            PairMethod.BOTH_ADJACENT.plannedFlags(),
            CustomPair.DEFAULT.first.flags to CustomPair.DEFAULT.second.flags,
        )
    }

    @Test
    fun customStartsInOrderWithTheDelayBetweenThem() {
        val recorder = Recorder()
        val pair = PairPreset.RESET_BOTH.pair
        val outcomes =
            runBlocking {
                CustomStrategy(pair) { recorder.events += "pause $it" }.launch(notes, maps, recorder, 450)
            }
        val flags = newTask or adjacent or clear
        assertEquals(
            listOf("start com.example.notes $flags", "pause 450", "start com.example.maps $flags"),
            recorder.events,
        )
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it.ok })
    }

    @Test
    fun customWithNoDelayDoesNotPause() {
        val recorder = Recorder()
        runBlocking {
            CustomStrategy(CustomPair()) { recorder.events += "pause $it" }.launch(notes, maps, recorder, 0)
        }
        assertEquals(2, recorder.events.size)
    }

    @Test
    fun oneCallSendsOneStartActivitiesAndIgnoresTheDelay() {
        val recorder = Recorder()
        val outcomes =
            runBlocking {
                CustomStrategy(PairPreset.ONE_CALL_RESET_BOTH.pair) { recorder.events += "pause $it" }
                    .launch(notes, maps, recorder, 900)
            }
        assertEquals(
            listOf("startAll com.example.notes ${newTask or clear}, com.example.maps ${newTask or adjacent or clear}"),
            recorder.events,
        )
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single().step.contains("startActivities"))
    }

    @Test
    fun aFailingStartIsRecordedAndTheSecondStillRuns() {
        val failing =
            object : PairStarter {
                override fun start(start: PairStart) {
                    if (start.target == notes) throw SecurityException("blocked")
                }

                override fun startAll(starts: List<PairStart>) = Unit

                override fun startTrampoline(request: TrampolineRequest) = Unit
            }
        val outcomes = runBlocking { CustomStrategy(CustomPair()) {}.launch(notes, maps, failing, 0) }
        assertEquals("SecurityException: blocked", outcomes[0].error)
        assertTrue(outcomes[1].ok)
    }

    @Test
    fun customIsTheMethodOfItsStrategyAndPlannedFlagsMatchWhatEveryMethodStarts() {
        assertEquals(PairMethod.CUSTOM, pairStrategyFor(PairMethod.CUSTOM).method)
        PairMethod.entries.filter { it != PairMethod.CUSTOM }.forEach { method ->
            val recorder = Recorder()
            val (a, b) = method.plannedFlags()
            runBlocking { pairStrategyFor(method) {}.launch(notes, maps, recorder, 0) }
            val text = recorder.events.joinToString()
            if (method.name.startsWith("TRAMPOLINE")) return@forEach
            assertTrue("$method $text", text.contains("com.example.notes $a") || text.contains("notes $a"))
            assertTrue("$method $text", text.contains("com.example.maps $b"))
        }
        val trampolineFirst = trampolineSteps(TrampolineRequest(notes, maps, adjacentFirst = true)).map { it.flags }
        assertEquals(PairMethod.TRAMPOLINE_ADJACENT_FIRST.plannedFlags().toList(), trampolineFirst)
        val trampolineSecond = trampolineSteps(TrampolineRequest(notes, maps, adjacentFirst = false)).map { it.flags }
        assertEquals(PairMethod.TRAMPOLINE_ADJACENT_SECOND.plannedFlags().toList(), trampolineSecond)
    }
}

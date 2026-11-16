package com.riffle.app.launcher.diagnostics.pair

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairScenarioTest {
    private val notes = PairTarget("com.example.notes", "com.example.notes.Main", "Notes")
    private val maps = PairTarget("com.example.maps", "com.example.maps.Main", "Maps")
    private val facts =
        PairFactsSource { _, _ ->
            PairFacts(34, "Acme", "Fold", false, "412x915", "compact", "PHONE", emptyList(), emptyList())
        }
    private val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
    private val adjacent = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    private val multiple = Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    @Test
    fun theScenariosAreInTheOrderTheOwnerRunsThemWithTheirNames() {
        assertEquals(
            listOf(
                "Reset both",
                "One call, reset both",
                "Fresh both",
                "Reuse both",
                "Reuse first, fresh second",
                "Fresh first, reuse second",
                "One call",
            ),
            PairScenario.entries.map { it.logName },
        )
    }

    @Test
    fun everyScenarioStandsForItsPresetOrRoundOneRecipe() {
        val byPreset =
            mapOf(
                PairScenario.RESET_BOTH to PairPreset.RESET_BOTH,
                PairScenario.ONE_CALL_RESET_BOTH to PairPreset.ONE_CALL_RESET_BOTH,
                PairScenario.FRESH_BOTH to PairPreset.FRESH_BOTH,
                PairScenario.REUSE_BOTH to PairPreset.REUSE_BOTH,
                PairScenario.REUSE_FIRST_FRESH_SECOND to PairPreset.REUSE_FIRST_FRESH_SECOND,
                PairScenario.FRESH_FIRST_REUSE_SECOND to PairPreset.FRESH_FIRST_REUSE_SECOND,
            )
        byPreset.forEach { (scenario, preset) ->
            assertEquals(scenario.name, PairMethod.CUSTOM, scenario.method)
            assertEquals(scenario.name, preset.pair, scenario.custom)
        }
        assertEquals(PairMethod.START_ACTIVITIES_ARRAY, PairScenario.ONE_CALL.method)
        assertEquals(
            PairMethod.START_ACTIVITIES_ARRAY.plannedFlags(),
            PairScenario.ONE_CALL.custom.first.flags to PairScenario.ONE_CALL.custom.second.flags,
        )
        assertEquals(newTask to (newTask or adjacent or multiple), PairMethod.START_ACTIVITIES_ARRAY.plannedFlags())
        assertTrue(PairScenario.ONE_CALL.custom.oneCall)
    }

    @Test
    fun theNamesAreUniqueAndLookUpTheirScenario() {
        assertEquals(PairScenario.entries.size, PairScenario.entries.map { it.logName }.toSet().size)
        PairScenario.entries.forEach { assertEquals(it, PairScenario.fromLogName(it.logName)) }
        assertNull(PairScenario.fromLogName(null))
        assertNull(PairScenario.fromLogName("Custom"))
    }

    @Test
    fun aScenarioLaunchNamesItselfInTheEntryAndTheCopiedText() {
        val calls = mutableListOf<String>()
        val starter =
            object : PairStarter {
                override fun start(start: PairStart) {
                    calls += "start ${start.target.packageName} ${start.flags}"
                }

                override fun startAll(starts: List<PairStart>) {
                    calls += "startAll"
                }

                override fun startTrampoline(request: TrampolineRequest) = Unit
            }
        val experiment = AppPairExperiment(clock = { 0L }, pause = {})
        val scenario = PairScenario.RESET_BOTH
        runBlocking {
            experiment.launch(
                scenario.method,
                notes,
                maps,
                600,
                starter,
                facts,
                PairLaunchOptions(scenario.custom, scenario = scenario.logName),
            )
        }
        val flags = newTask or adjacent or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(listOf("start com.example.notes $flags", "start com.example.maps $flags"), calls)
        val text = experiment.log.value.single().toPlainText(java.time.ZoneOffset.UTC)
        assertTrue(text, text.contains("Reset both (Custom)  delay 600 ms"))
        assertTrue(text, text.contains("flags first:  LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK"))
    }
}

package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PairScriptLogTest {
    private val notes = PairTarget("com.example.notes", "com.example.notes.Main", "Notes")
    private val maps = PairTarget("com.example.maps", "com.example.maps.Main", "Maps")
    private val facts =
        PairFactsSource { _, _ ->
            PairFacts(36, "Acme", "Fold 9", false, "724x784", "medium", "UNFOLDED", emptyList(), emptyList())
        }
    private val quiet =
        object : PairStarter {
            override fun start(start: PairStart) = Unit

            override fun startAll(starts: List<PairStart>) = Unit

            override fun startTrampoline(request: TrampolineRequest) = Unit
        }

    private fun launcher(experiment: AppPairExperiment) =
        ExperimentScriptLauncher(experiment, { quiet }, { facts }, PairLaunchWatcher.None, { null }, {})

    private fun fire(
        experiment: AppPairExperiment,
        runId: Long,
        index: Int,
    ): Long {
        val step = PairScript.steps[index]
        val info = PairScriptInfo(runId, index + 1, 9, step.bothOpen, step.bothOpen)
        return runBlocking { launcher(experiment).fire(step, info, notes, maps) }
    }

    @Test
    fun aFiredStepWritesTheNormalEntryWithTheScriptLines() {
        val e = AppPairExperiment(clock = { 1_000L }, pause = {})
        val id = fire(e, 1_000L, 2)
        e.updateScript(id) { it.copy(returnAttempt = "ok", returned = "automatically after 120 ms") }
        val entry = e.log.value.single()
        assertEquals("Reset both", entry.scenario)
        val text = entry.toPlainText(ZoneOffset.UTC)
        listOf(
            "Reset both (Custom)  delay 600 ms",
            "flags first:  LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK",
            "script: step 3 of 9",
            "already open: first=true second=true",
            "return attempt: ok",
            "returned to launcher: automatically after 120 ms",
            "launcher lifecycle trace",
            "device: Android SDK 36, Acme Fold 9",
        ).forEach { assertTrue("$it in\n$text", text.contains(it)) }
    }

    @Test
    fun theWarmUpIsLoggedAsAStepWithNothingOpenYet() {
        val e = AppPairExperiment(clock = { 1_000L }, pause = {})
        fire(e, 1_000L, 0)
        val entry = e.log.value.single()
        assertEquals("Warm-up: open both", entry.scenario)
        assertEquals(PairScript.WARM_UP_RECIPE, entry.custom)
        val text = entry.toPlainText(ZoneOffset.UTC)
        assertTrue(text, text.contains("script: step 1 of 9"))
        assertTrue(text, text.contains("already open: first=false second=false"))
    }

    @Test
    fun anEntryWithoutScriptFactsPrintsNoScriptLines() {
        val e = AppPairExperiment(clock = { 1_000L }, pause = {})
        runBlocking { e.launch(PairMethod.BOTH_ADJACENT, notes, maps, 0, quiet, facts) }
        val text = e.log.value.single().toPlainText(ZoneOffset.UTC)
        assertFalse(text.contains("script:"))
        assertFalse(text.contains("returned to launcher"))
    }

    @Test
    fun theScriptLogHasOneHeaderThenThisRunsStepsOldestFirst() {
        var now = 0L
        val e = AppPairExperiment(clock = { now }, pause = {})
        now = 10_000L
        fire(e, 10_000L, 0)
        e.updateScript(1) { it.copy(notes = listOf("launcher stayed in front")) }
        now = 20_000L
        fire(e, 10_000L, 1)
        runBlocking { e.launch(PairMethod.BOTH_ADJACENT, notes, maps, 0, quiet, facts) }

        val text = e.log.value.toScriptPlainText(zone = ZoneOffset.UTC)
        assertTrue(text, text.startsWith("App pair script log (oldest first)\n"))
        listOf(
            "device: Android SDK 36, Acme Fold 9",
            "window: 724x784 dp (medium), layout: UNFOLDED",
            "app pair: first com.example.notes, second com.example.maps",
            "started: 1970-01-01 00:00:10",
            "note: launcher stayed in front",
        ).forEach { assertTrue("$it in\n$text", text.contains(it)) }
        assertEquals(1, Regex("started:").findAll(text).count())
        assertTrue(text.indexOf("started:") < text.indexOf("script: step 1 of 9"))
        assertTrue(text.indexOf("script: step 1 of 9") < text.indexOf("script: step 2 of 9"))
        assertFalse("hand-made launches are left out", text.contains("Both adjacent"))
    }

    @Test
    fun onlyTheNewestRunIsCopiedUnlessOneIsNamed() {
        val e = AppPairExperiment(clock = { 0L }, pause = {})
        fire(e, 1L, 0)
        fire(e, 2L, 0)
        fire(e, 2L, 1)
        val newest = e.log.value.toScriptPlainText(zone = ZoneOffset.UTC)
        assertEquals(2, Regex("script: step").findAll(newest).count())
        val older = e.log.value.toScriptPlainText(runId = 1L, zone = ZoneOffset.UTC)
        assertEquals(1, Regex("script: step").findAll(older).count())
        assertTrue(emptyList<PairLogEntry>().toScriptPlainText().contains("no script entries yet"))
    }
}

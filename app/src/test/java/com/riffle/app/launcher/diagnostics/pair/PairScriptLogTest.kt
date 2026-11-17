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

    private val features = listOf("FEATURE_FREEFORM_WINDOW_MANAGEMENT=false", "FEATURE_PICTURE_IN_PICTURE=true")
    private val targets = listOf("com.example.notes: screenOrientation=-1, FLAG_STOPPED=false")
    private val manyFacts =
        PairFacts(37, "Acme", "Fold 9", false, "724x784", "medium", "UNFOLDED", features, targets)

    private fun heldEntry(
        variant: HeldVariant,
        index: Int,
        runId: Long,
        kind: PairScriptKind,
        total: Int,
    ) = PairLogEntry(
        id = runId * 100 + index,
        timeMillis = runId + index * 10_000L,
        method = PairMethod.CUSTOM,
        delayMillis = 600,
        first = notes.packageName,
        second = maps.packageName,
        outcomes = listOf(StartOutcome("holder activity ${variant.id} [NEW_TASK]")),
        facts = manyFacts,
        trace = (1..8).map { PairObservation(it * 150L, "onPause window ${it * 3}") },
        scenario = variant.title,
        script =
            PairScriptInfo(
                runId,
                index,
                total,
                index > 1,
                index > 1,
                returnAttempt = "ok",
                returned = "automatically after 120 ms",
                openUnknown = index == 1,
                kind = kind,
            ),
        held = variant,
        holder = FakeHolderRuns.collapsingTrace(variant),
    )

    private fun heldRun(
        runId: Long,
        variants: List<HeldVariant>,
        kind: PairScriptKind,
    ) = variants.mapIndexed { i, v -> heldEntry(v, i + 1, runId, kind, variants.size) }

    @Test
    fun aSixStepHeldRunCopiesInUnderTwelveThousandCharactersWithTheHeaderOnce() {
        val log = heldRun(5_000L, HeldVariants.all, PairScriptKind.HELD).reversed()
        val text = log.toScriptPlainText(zone = ZoneOffset.UTC)
        assertTrue("${text.length} characters:\n$text", text.length < 12_000)
        assertEquals(1, Regex("device: Android SDK").findAll(text).count())
        assertEquals(1, Regex("window: ").findAll(text).count())
        assertEquals(1, Regex("features: ").findAll(text).count())
        assertEquals(1, Regex("target: ").findAll(text).count())
        listOf("feature: ", "owner's verdict", "duplicates in Recents", "already open (ticked")
            .forEach { assertTrue("$it must not repeat per entry in\n$text", !text.contains(it)) }
        // The holder's own facts are the same in every entry, so they are printed once, in the header.
        assertEquals(1, Regex("holder activityInfo: ").findAll(text).count())
        assertEquals(1, Regex("holder holds the HOME role: true").findAll(text).count())
        assertTrue(text, !text.contains("fact: "))
    }

    @Test
    fun everyCompactEntryHasAtMostEightLinesThatAreNotTraceLines() {
        val log = heldRun(5_000L, HeldVariants.all, PairScriptKind.HELD)
        log.forEach { entry ->
            val lines = entry.toCompactText(ZoneOffset.UTC).lines().filter { it.isNotBlank() }
            val overhead = lines.filterNot { it.startsWith("    +") }
            assertTrue("${overhead.size} overhead lines in\n${lines.joinToString("\n")}", overhead.size <= 8)
        }
    }

    @Test
    fun aHolderThatNeverRanStillHasASinglePlaceholderLine() {
        val entry =
            heldEntry(HeldVariants.all[0], 1, 1L, PairScriptKind.HELD, 6).copy(holder = null, trace = emptyList())
        val text = entry.toCompactText(ZoneOffset.UTC)
        assertTrue(text, text.contains("holder trace: nothing reported (the holder never ran)"))
        assertTrue(text, text.contains("launcher lifecycle trace: nothing recorded"))
    }

    @Test
    fun theOwnersVerdictAndDuplicatesArePrintedOnlyWhenSet() {
        val plain = heldEntry(HeldVariants.all[0], 1, 1L, PairScriptKind.HELD, 6)
        assertTrue(!plain.toCompactText(ZoneOffset.UTC).contains("verdict"))
        val set = plain.copy(verdict = PairVerdict.SPLIT, duplicatesInRecents = true).toCompactText(ZoneOffset.UTC)
        assertTrue(set, set.contains("owner's verdict: Split") && set.contains("duplicates in Recents: yes"))
    }

    @Test
    fun aNoSecondProbeSaysSoInsteadOfPrintingSecondFlags() {
        val entry = heldEntry(HeldVariants.probes[0], 1, 1L, PairScriptKind.PROBES, 5)
        val compact = entry.toCompactText(ZoneOffset.UTC)
        assertTrue(compact, compact.contains("second: none"))
        assertTrue(compact, compact.contains("Split probe"))
        assertTrue(entry.toPlainText(ZoneOffset.UTC).contains("flags second: none (no second start)"))
    }

    @Test
    fun theCopyTakesTheMostRecentlyStartedRunOfAnyKindOrOfTheKindAsked() {
        val held = heldRun(1_000L, HeldVariants.all.take(2), PairScriptKind.HELD)
        val probes = heldRun(3_000L, HeldVariants.probes.take(2), PairScriptKind.PROBES)
        val nine =
            listOf(
                heldEntry(HeldVariants.all[0], 1, 2_000L, PairScriptKind.SCENARIOS, 9).copy(held = null, holder = null),
            )
        // Newest first, as the log is kept; the probe run was started last although its entries are not first.
        val log = probes.reversed() + nine + held.reversed()

        fun head(text: String) = text.lines()[1]

        assertEquals("script: split probe script (five steps)", head(log.toScriptPlainText(zone = ZoneOffset.UTC)))
        assertEquals(
            "script: held-trampoline script (six steps)",
            head(log.toScriptPlainText(kind = PairScriptKind.HELD, zone = ZoneOffset.UTC)),
        )
        assertEquals(
            "script: nine-step scenario script",
            head(log.toScriptPlainText(kind = PairScriptKind.SCENARIOS, zone = ZoneOffset.UTC)),
        )
        val onlyHeld = held.reversed()
        assertEquals(
            "no script entries yet",
            onlyHeld.toScriptPlainText(kind = PairScriptKind.PROBES, zone = ZoneOffset.UTC).lines()[1],
        )
        // A held run started after the nine-step one wins "any" even when the older run has more steps.
        val later = heldRun(9_000L, HeldVariants.all.take(1), PairScriptKind.HELD)
        assertEquals(
            "script: held-trampoline script (six steps)",
            head((later + probes.reversed() + nine).toScriptPlainText(zone = ZoneOffset.UTC)),
        )
    }
}

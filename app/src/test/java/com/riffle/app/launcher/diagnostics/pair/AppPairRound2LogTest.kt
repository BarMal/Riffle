package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AppPairRound2LogTest {
    private val notes = PairTarget("com.example.notes", "com.example.notes.Main", "Notes")
    private val maps = PairTarget("com.example.maps", "com.example.maps.Main", "Maps")
    private val facts =
        PairFactsSource { _, _ ->
            PairFacts(
                34, "Acme", "Fold 9", false, "724x784", "medium", "UNFOLDED",
                listOf(
                    "freeform=false",
                ),
                emptyList(),
            )
        }
    private val quiet =
        object : PairStarter {
            override fun start(start: PairStart) = Unit

            override fun startAll(starts: List<PairStart>) = Unit

            override fun startTrampoline(request: TrampolineRequest) = Unit
        }

    private class FakeWatcher : PairLaunchWatcher {
        var launchMillis: Long? = null
        var sink: ((List<PairObservation>, Boolean) -> Unit)? = null

        override fun begin(
            launchMillis: Long,
            onTrace: (List<PairObservation>, Boolean) -> Unit,
        ) {
            this.launchMillis = launchMillis
            sink = onTrace
        }
    }

    private fun experiment() = AppPairExperiment(clock = { 5_000L }, pause = {})

    @Test
    fun aCustomLaunchRecordsItsRecipeTheOpenTicksAndTheWatcherTrace() {
        val e = experiment()
        val watcher = FakeWatcher()
        runBlocking {
            e.launch(
                PairMethod.CUSTOM,
                notes,
                maps,
                600,
                quiet,
                facts,
                PairLaunchOptions(PairPreset.RESET_BOTH.pair, true, false, watcher),
            )
        }
        assertEquals(5_000L, watcher.launchMillis)
        watcher.sink!!.invoke(listOf(PairObservation(40, "onPause")), true)
        val entry = e.log.value.single()
        assertEquals(PairPreset.RESET_BOTH.pair, entry.custom)
        assertTrue(entry.firstWasOpen)
        assertFalse(entry.secondWasOpen)
        assertEquals(listOf(PairObservation(40, "onPause")), entry.trace)
        assertTrue(entry.traceOpen)
        watcher.sink!!.invoke(listOf(PairObservation(40, "onPause")), false)
        assertFalse(e.log.value.single().traceOpen)
    }

    @Test
    fun theVerdictIsWrittenIntoTheEntryAndTheSameChoiceAgainClearsIt() {
        val e = experiment()
        runBlocking { e.launch(PairMethod.BOTH_ADJACENT, notes, maps, 0, quiet, facts) }
        val id = e.log.value.single().id
        PairVerdict.entries.forEach { verdict ->
            e.setVerdict(id, verdict)
            assertEquals(verdict, e.log.value.single().verdict)
        }
        e.setVerdict(id, PairVerdict.SPLIT_UNEVEN)
        assertNull(e.log.value.single().verdict)
        e.setDuplicates(id, true)
        assertTrue(e.log.value.single().duplicatesInRecents)
    }

    @Test
    fun theVerdictOnlyChangesItsOwnEntry() {
        val e = experiment()
        runBlocking {
            e.launch(PairMethod.SEQUENTIAL, notes, maps, 0, quiet, facts)
            e.launch(PairMethod.SEQUENTIAL, maps, notes, 0, quiet, facts)
        }
        val (newest, older) = e.log.value
        e.setVerdict(older.id, PairVerdict.NO_SPLIT)
        assertNull(e.log.value.first { it.id == newest.id }.verdict)
        assertEquals(PairVerdict.NO_SPLIT, e.log.value.first { it.id == older.id }.verdict)
    }

    @Test
    fun theCopiedTextCarriesEveryRound2FieldNewestFirst() {
        val e = experiment()
        val watcher = FakeWatcher()
        runBlocking {
            e.launch(PairMethod.SEQUENTIAL, notes, maps, 600, quiet, facts)
            e.launch(
                PairMethod.CUSTOM,
                notes,
                maps,
                600,
                quiet,
                facts,
                PairLaunchOptions(PairPreset.ONE_CALL_RESET_BOTH.pair, true, watcher = watcher),
            )
        }
        watcher.sink!!.invoke(
            listOf(PairObservation(0, "launcher window at launch: x"), PairObservation(75, "onPause")),
            false,
        )
        val newest = e.log.value.first().id
        e.setVerdict(newest, PairVerdict.SPLIT_UNEVEN)
        e.setDuplicates(newest, true)
        val text = e.log.value.toPlainText(ZoneOffset.UTC)
        listOf(
            "Custom  one startActivities call, delay ignored",
            "flags first:  NEW_TASK|CLEAR_TASK",
            "flags second: LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK",
            "already open (ticked by the owner): first yes, second no",
            "startActivities [first com.example.notes",
            "launcher lifecycle trace",
            "+75 ms onPause",
            "owner's verdict: Split, uneven",
            "duplicates in Recents: yes",
            "Android SDK 34, Acme Fold 9",
            "724x784 dp (medium)",
        ).forEach { assertTrue("$it in\n$text", text.contains(it)) }
        assertTrue(text.indexOf("Custom") < text.indexOf("Sequential"))
        assertTrue(text.contains("Sequential  delay 600 ms"))
        assertTrue(text.contains("flags first:  NEW_TASK\n"))
        assertTrue(text.contains("owner's verdict: not noted yet"))
        assertTrue(text.contains("duplicates in Recents: no (not ticked)"))
        assertTrue(text.contains("nothing recorded"))
    }

    @Test
    fun anOpenTraceIsMarkedAsStillWatching() {
        val e = experiment()
        val watcher = FakeWatcher()
        runBlocking { e.launch(PairMethod.CUSTOM, notes, maps, 0, quiet, facts, PairLaunchOptions(watcher = watcher)) }
        watcher.sink!!.invoke(listOf(PairObservation(1, "onStop")), true)
        assertTrue(e.log.value.toPlainText(ZoneOffset.UTC).contains("(still watching)"))
    }
}

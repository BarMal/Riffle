package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AppPairExperimentTest {
    private val a = PairTarget("com.a", "com.a.Main", "A")
    private val b = PairTarget("com.b", "com.b.Main", "B")
    private val facts =
        PairFactsSource { _, _ ->
            PairFacts(
                34,
                "Acme",
                "Fold 9",
                false,
                "412x915",
                "compact",
                "FOLDABLE (UNFOLDED)",
                listOf("freeform=false"),
                listOf("com.a: x"),
            )
        }
    private val quiet =
        object : PairStarter {
            override fun start(start: PairStart) = Unit

            override fun startAll(starts: List<PairStart>) = Unit

            override fun startTrampoline(request: TrampolineRequest) = Unit
        }
    private val failing =
        object : PairStarter {
            override fun start(start: PairStart) {
                throw SecurityException("nope")
            }

            override fun startAll(starts: List<PairStart>) = Unit

            override fun startTrampoline(request: TrampolineRequest) = Unit
        }

    private fun experiment() = AppPairExperiment(clock = { 1_000L }, pause = {})

    @Test
    fun theLogIsNewestFirstAndKeepsFailuresWithoutThrowing() {
        val e = experiment()
        runBlocking {
            e.launch(PairMethod.BOTH_ADJACENT, a, b, 0, quiet, facts)
            e.launch(PairMethod.BOTH_ADJACENT, b, a, 0, failing, facts)
        }
        val log = e.log.value
        assertEquals(listOf("com.b", "com.a"), log.map { it.first })
        assertTrue(log[1].outcomes.all { it.ok })
        assertFalse(log[0].outcomes[0].ok)
    }

    @Test
    fun aTrampolineReportIsAddedToTheEntryThatUsedIt() {
        val e = experiment()
        runBlocking { e.launch(PairMethod.TRAMPOLINE_ADJACENT_FIRST, a, b, 0, quiet, facts) }
        e.onTrampolineReport(listOf(StartOutcome("trampoline step 1"), StartOutcome("trampoline step 2", "X: y")))
        e.onTrampolineReport(listOf(StartOutcome("ignored")))
        assertEquals(3, e.log.value.single().outcomes.size)
    }

    @Test
    fun theCopiedTextHasEverythingAndNoMoreThanPackagesAndDeviceFacts() {
        val e = experiment()
        runBlocking { e.launch(PairMethod.SEQUENTIAL, a, b, 600, failing, facts) }
        val text = e.log.value.toPlainText(ZoneOffset.UTC)
        listOf(
            "00:00:01.000",
            "Sequential",
            "delay 600 ms",
            "first:  com.a",
            "second: com.b",
            "FAILED",
            "SecurityException: nope",
            "Android SDK 34, Acme Fold 9",
            "412x915 dp (compact)",
            "multi-window at launch: false",
            "FOLDABLE (UNFOLDED)",
            "feature: freeform=false",
        ).forEach { assertTrue("$it in\n$text", text.contains(it)) }
        assertFalse(text.contains("\"A\""))
    }

    @Test
    fun anEmptyLogSaysSo() {
        assertTrue(emptyList<PairLogEntry>().toPlainText().contains("no launches yet"))
    }

    @Test
    fun widthClassesFollowTheMaterialBreakpoints() {
        assertEquals(listOf("compact", "medium", "expanded"), listOf(599, 600, 840).map { widthClassFor(it) })
    }
}

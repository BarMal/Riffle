package com.riffle.app.launcher.diagnostics.pair

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldVariantTest {
    private val adjacent = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK
    private val plain = Intent.FLAG_ACTIVITY_NEW_TASK
    private val clear = Intent.FLAG_ACTIVITY_CLEAR_TASK
    private val multiple = Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    private fun variant(id: String) = HeldVariants.all.single { it.id == id }

    @Test
    fun thereAreSixVariantsInTheAgreedOrderWithTheirTitles() {
        assertEquals(
            listOf(
                "H1" to "Held: adjacent, then plain",
                "H2" to "Held: adjacent, then adjacent",
                "H3" to "Held: reset both",
                "H4" to "Held: fresh both",
                "H5" to "Held: adjacent, then plain, hold longer",
                "H6" to "Held: plain first, then adjacent",
            ),
            HeldVariants.all.map { it.id to it.title },
        )
    }

    @Test
    fun flagsAndFinishDelaysAreTheAgreedOnes() {
        val expected =
            mapOf(
                "H1" to Triple(adjacent, plain, 300L),
                "H2" to Triple(adjacent, adjacent, 300L),
                "H3" to Triple(adjacent or clear, plain or clear, 300L),
                "H4" to Triple(adjacent or multiple, plain or multiple, 300L),
                "H5" to Triple(adjacent, plain, 800L),
                "H6" to Triple(plain, adjacent, 300L),
            )
        expected.forEach { (id, flagsAndDelay) ->
            val v = variant(id)
            assertEquals(id, flagsAndDelay, Triple(v.firstFlags, v.secondFlags, v.finishDelayMillis))
        }
    }

    @Test
    fun onlyTheControlStartsItsSecondAppOnATimerInsteadOfWaitingForTheSplit() {
        HeldVariants.all.forEach {
            if (it.id == "H6") assertEquals(600L, it.secondAfterMillis) else assertNull(it.id, it.secondAfterMillis)
        }
    }

    @Test
    fun everyVariantStartsTheSecondAppAnywayWhenNoSplitShowsUp() {
        HeldVariants.all.forEach { assertEquals(it.id, TimeoutAction.START_SECOND, it.onTimeout) }
    }

    @Test
    fun theToastNamesTheStepAndTheRecipeWithoutThePrefix() {
        val steps = PairScript.heldSteps
        assertEquals(6, steps.size)
        assertEquals("Held 2 of 6: adjacent, then adjacent", steps[1].toastText(2, 6))
        assertEquals(HeldVariants.all.map { it.title }, steps.map { it.logName })
        steps.forEach { assertEquals(6_000L, it.dwellMillis) }
        assertTrue(steps[0].openUnknown && steps.drop(1).none { it.openUnknown })
        assertTrue(!steps[0].bothOpen && steps.drop(1).all { it.bothOpen })
    }

    @Test
    fun byIdFindsAVariantAndNothingForAnUnknownId() {
        assertEquals(variant("H4"), HeldVariants.byId("H4"))
        assertNull(HeldVariants.byId("H9"))
        assertNull(HeldVariants.byId(null))
    }

    private val hold = 5_000L

    @Test
    fun thereAreFiveProbesWithTheAgreedFlagsAndTimings() {
        val probes = HeldVariants.probes
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5"), probes.map { it.id })
        assertTrue(probes.all { it.probe && it.secondAfterMillis == null })
        val p1 = probes[0]
        val p2 = probes[1]
        val p3 = probes[2]
        val p4 = probes[3]
        val p5 = probes[4]
        assertEquals(Triple(adjacent, false, hold), Triple(p1.firstFlags, p1.startsSecond, p1.finishDelayMillis))
        assertEquals(Triple(adjacent, false, hold), Triple(p2.firstFlags, p2.startsSecond, p2.finishDelayMillis))
        assertEquals(Triple(adjacent, plain, hold), Triple(p3.firstFlags, p3.secondFlags, p3.finishDelayMillis))
        assertEquals(Triple(adjacent, plain, 300L), Triple(p4.firstFlags, p4.secondFlags, p4.finishDelayMillis))
        assertEquals(2_500L, p4.splitSettleMillis)
        assertEquals(adjacent or multiple, p5.firstFlags)
        assertEquals(Pair(plain, hold), Pair(p5.secondFlags, p5.finishDelayMillis))
        assertEquals(listOf(false, true, false, false, false), probes.map { it.listedHolder })
        assertEquals(400L, p3.splitSettleMillis)
    }

    @Test
    fun theHeldVariantsAreUnchangedByTheProbes() {
        assertEquals(listOf("H1", "H2", "H3", "H4", "H5", "H6"), HeldVariants.all.map { it.id })
        assertTrue(HeldVariants.all.none { it.probe || !it.startsSecond || it.listedHolder })
        assertTrue(HeldVariants.all.all { it.splitSettleMillis == 400L })
        assertEquals(HeldVariants.probes[2], HeldVariants.byId("P3"))
    }

    @Test
    fun theProbeScriptHasFiveStepsWithSevenSecondDwellsAndProbeToasts() {
        val steps = PairScript.probeSteps
        assertEquals(5, steps.size)
        assertEquals("Probe 2 of 5: hold only, listed in Recents", steps[1].toastText(2, 5))
        assertEquals(HeldVariants.probes.map { it.title }, steps.map { it.logName })
        steps.forEach { assertEquals(7_000L, it.dwellMillis) }
        assertTrue(steps[0].openUnknown && steps.drop(1).none { it.openUnknown })
    }
}

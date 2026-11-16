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
}

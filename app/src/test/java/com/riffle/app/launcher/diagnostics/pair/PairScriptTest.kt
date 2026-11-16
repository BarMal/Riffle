package com.riffle.app.launcher.diagnostics.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PairScriptTest {
    private val steps = PairScript.steps

    @Test
    fun theScriptHasTheNineStepsInTheAgreedOrder() {
        assertEquals(
            listOf(
                "Warm-up: open both",
                "Reuse both",
                "Reset both",
                "One call, reset both",
                "One call (round 1)",
                "Reuse first, fresh second (round 1's Both adjacent)",
                "Fresh first, reuse second",
                "Fresh both",
                "Reset both, again",
            ),
            steps.map { it.name },
        )
    }

    @Test
    fun everyStepAfterTheWarmUpRepeatsItsScenariosRecipe() {
        val scenarios =
            listOf(
                PairScenario.REUSE_BOTH,
                PairScenario.RESET_BOTH,
                PairScenario.ONE_CALL_RESET_BOTH,
                PairScenario.ONE_CALL,
                PairScenario.REUSE_FIRST_FRESH_SECOND,
                PairScenario.FRESH_FIRST_REUSE_SECOND,
                PairScenario.FRESH_BOTH,
                PairScenario.RESET_BOTH,
            )
        assertEquals(scenarios, steps.drop(1).map { it.scenario })
        steps.drop(1).forEach {
            assertEquals(it.name, it.scenario?.method, it.method)
            assertEquals(it.name, it.scenario?.custom, it.custom)
            assertEquals(it.scenario?.logName, it.logName)
        }
    }

    @Test
    fun roundOnesOneCallAndBothAdjacentAreTheRoundOneStrategies() {
        assertEquals(PairMethod.START_ACTIVITIES_ARRAY, steps[4].method)
        assertEquals(
            PairMethod.BOTH_ADJACENT.plannedFlags(),
            steps[5].method.plannedFlags(steps[5].custom),
        )
    }

    @Test
    fun theWarmUpOpensBothAppsWithPlainNewTaskAndIsNotAResult() {
        val warmUp = steps.first()
        assertNull(warmUp.scenario)
        assertEquals("Warm-up: open both", warmUp.logName)
        assertEquals(PairFlags.NEW_TASK, warmUp.custom.first.flags)
        assertEquals(PairFlags.NEW_TASK, warmUp.custom.second.flags)
        assertEquals(false, warmUp.custom.oneCall)
        assertSame(PairScript.WARM_UP_RECIPE, warmUp.custom)
    }

    @Test
    fun bothAppsAreExpectedOpenFromTheFirstRealStepOn() {
        assertEquals(listOf(false) + List(8) { true }, steps.map { it.bothOpen })
    }

    @Test
    fun theTimingsAreTheAgreedOnes() {
        assertEquals(1_000L, PairScript.SETTLE_MS)
        assertEquals(4_000L, PairScript.DWELL_MS)
        assertEquals(20_000L, PairScript.RETURN_WAIT_MS)
    }

    @Test
    fun theOwnersPairIsHardCodedHere() {
        assertEquals("com.github.android", PairScriptApps.FIRST_PACKAGE)
        assertEquals("net.waterfox.android.release", PairScriptApps.SECOND_PACKAGE)
    }
}

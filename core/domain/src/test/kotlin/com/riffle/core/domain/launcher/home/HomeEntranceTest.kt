package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeEntranceTest {
    @Test
    fun itStartsInvisibleAndShiftedTowardTheDocksEdgeAndEndsInPlace() {
        assertEquals(0f, HomeEntrance.alpha(0f))
        assertEquals(HomeEntrance.RISE_DP, HomeEntrance.offsetDp(0f, reducedMotion = false))
        assertEquals(1f, HomeEntrance.alpha(1f))
        assertEquals(0f, HomeEntrance.offsetDp(1f, reducedMotion = false))
    }

    @Test
    fun theRiseIsSubtle() {
        assertTrue(HomeEntrance.RISE_DP in 12f..16f)
    }

    @Test
    fun itNeverLeavesItsRangeWhateverTheSpringOvershootsTo() {
        assertEquals(0f, HomeEntrance.alpha(-0.2f))
        assertEquals(1f, HomeEntrance.alpha(1.2f))
        assertEquals(0f, HomeEntrance.offsetDp(1.2f, reducedMotion = false))
        assertEquals(HomeEntrance.RISE_DP, HomeEntrance.offsetDp(-0.2f, reducedMotion = false))
    }

    @Test
    fun underReducedMotionItIsAPlainFadeWithNoRise() {
        listOf(0f, 0.3f, 0.7f, 1f).forEach { progress ->
            assertEquals(0f, HomeEntrance.offsetDp(progress, reducedMotion = true))
        }
        assertEquals(0.3f, HomeEntrance.alpha(0.3f))
    }

    @Test
    fun itFadesAndRisesTogether() {
        val early = HomeEntrance.offsetDp(0.25f, reducedMotion = false)
        val late = HomeEntrance.offsetDp(0.75f, reducedMotion = false)
        assertTrue(early > late)
        assertTrue(HomeEntrance.alpha(0.25f) < HomeEntrance.alpha(0.75f))
    }
}

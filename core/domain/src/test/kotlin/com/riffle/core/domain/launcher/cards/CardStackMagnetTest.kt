package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [CardStackMagnet] turns one strength knob into the two things that decide how a card stack's
 * scroll position leaves the spot its own fling stopped at: how long it stands there first, and how
 * briskly it then travels onto the nearest card.
 */
class CardStackMagnetTest {
    @Test
    fun theDefaultStrengthMatchesTheReferenceLauncher() {
        // Calm's own CardStackTuning.magnetStrength default, so an untouched Riffle install starts
        // from the same feel rather than from an arbitrary midpoint.
        assertEquals(70, CardStackMagnet().strengthPercent)
    }

    @Test
    fun aStrongerMagnetWaitsLessBeforePullingHome() {
        val weakest = CardStackMagnet(strengthPercent = MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT)
        val strongest = CardStackMagnet(strengthPercent = MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT)

        // Calm's magnetDelayMillis range, kept verbatim.
        assertEquals(130L, weakest.settleDelayMillis)
        assertEquals(40L, strongest.settleDelayMillis)
    }

    @Test
    fun theSettleDelayFallsMonotonicallyAsStrengthRises() {
        val delays = (0..100 step 5).map { percent -> CardStackMagnet(percent).settleDelayMillis }

        assertEquals(delays.sortedDescending(), delays)
    }

    @Test
    fun aStrongerMagnetPullsHomeMoreFirmly() {
        val weakest = CardStackMagnet(strengthPercent = MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT)
        val strongest = CardStackMagnet(strengthPercent = MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT)

        assertTrue(weakest.stiffnessScale < 1f)
        assertTrue(strongest.stiffnessScale > 1f)
        assertTrue(strongest.stiffnessScale > weakest.stiffnessScale)
    }

    @Test
    fun theStiffnessScaleRisesMonotonicallyWithStrength() {
        val scales = (0..100 step 5).map { percent -> CardStackMagnet(percent).stiffnessScale }

        assertEquals(scales.sorted(), scales)
    }

    @Test
    fun theDefaultStrengthLeavesTheBaseSpringRoughlyUnchanged() {
        // The single fixed stiffness this replaced is the renderer's own unscaled base spring, so
        // an install that never touches the new slider keeps close to the motion it already had.
        val defaultScale = CardStackMagnet().stiffnessScale

        assertTrue(defaultScale in 0.85f..1.25f, "expected a near-unscaled default, got $defaultScale")
    }

    @Test
    fun aStrengthOutsideTheSupportedRangeIsRejectedRatherThanSilentlyClamped() {
        // Persisted settings are normalized through AdaptiveStageMotion.coerce before they ever
        // reach here, so an out-of-range value at this point is a programming error, not user data.
        assertFailsWith<IllegalArgumentException> {
            CardStackMagnet(strengthPercent = MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            CardStackMagnet(strengthPercent = MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT - 1)
        }
    }
}

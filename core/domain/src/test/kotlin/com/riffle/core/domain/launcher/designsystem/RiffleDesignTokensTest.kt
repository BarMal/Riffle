package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RiffleDesignTokensTest {
    @Test
    fun spacingScaleMatchesTheDesignLanguage() {
        assertEquals(listOf(2, 4, 8, 12, 16, 24, 32, 48), RiffleSpacingScale.steps)
    }

    @Test
    fun radiusScaleMatchesTheDesignLanguage() {
        assertEquals(listOf(8, 16, 24, 32), RiffleRadiusScale.steps)
    }

    @Test
    fun elevationHasFourAscendingLevels() {
        assertEquals(4, RiffleElevationScale.levels.size)
        assertEquals(RiffleElevationScale.levels.sorted(), RiffleElevationScale.levels)
        assertTrue(RiffleElevationScale.levels.all { level -> level > RiffleElevationScale.LEVEL_0 })
    }

    @Test
    fun thereAreExactlyThreeDistinctSprings() {
        assertEquals(3, RiffleMotionTokens.springs.toSet().size)
    }

    @Test
    fun springsGetSofterFromSnappyToGentle() {
        val snappy = RiffleMotionTokens.Snappy
        val smooth = RiffleMotionTokens.Smooth
        val gentle = RiffleMotionTokens.Gentle
        assertTrue(snappy.stiffness > smooth.stiffness)
        assertTrue(smooth.stiffness > gentle.stiffness)
        assertTrue(gentle.dampingRatio <= smooth.dampingRatio)
    }

    @Test
    fun noSpringWobbles() {
        // Calm by default: at most a hint of overshoot, never a visible bounce.
        assertTrue(RiffleMotionTokens.springs.all { spring -> spring.dampingRatio >= 0.75f })
    }

    @Test
    fun durationsAscend() {
        assertTrue(RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS < RiffleMotionTokens.DURATION_SHORT_MILLIS)
        assertTrue(RiffleMotionTokens.DURATION_SHORT_MILLIS < RiffleMotionTokens.DURATION_STANDARD_MILLIS)
        assertTrue(RiffleMotionTokens.DURATION_STANDARD_MILLIS < RiffleMotionTokens.DURATION_EMPHASIZED_MILLIS)
    }

    @Test
    fun springTokensRejectNonPositiveParameters() {
        assertFailsWith<IllegalArgumentException> { RiffleSpringTokens(dampingRatio = 0f, stiffness = 1f) }
        assertFailsWith<IllegalArgumentException> { RiffleSpringTokens(dampingRatio = 1f, stiffness = 0f) }
    }
}

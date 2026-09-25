package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReducedMotionPreferenceTest {
    @Test
    fun resolutionMatrixFollowsTheSystemOnlyWhenPreferenceIsSystem() {
        val expected =
            mapOf(
                (ReducedMotionPreference.SYSTEM to false) to false,
                (ReducedMotionPreference.SYSTEM to true) to true,
                (ReducedMotionPreference.ON to false) to true,
                (ReducedMotionPreference.ON to true) to true,
                (ReducedMotionPreference.OFF to false) to false,
                (ReducedMotionPreference.OFF to true) to false,
            )

        expected.forEach { (input, reduced) ->
            val (preference, system) = input
            assertEquals(reduced, preference.resolve(system), "$preference with system=$system")
            assertEquals(
                reduced,
                MotionSettings(reducedMotionPreference = preference, systemReducedMotion = system).reducedMotion,
                "MotionSettings $preference with system=$system",
            )
        }
    }

    @Test
    fun legacyBooleanMigratesTrueToOnAndFalseToSystem() {
        assertEquals(ReducedMotionPreference.ON, ReducedMotionPreference.fromLegacyReducedMotion(true))
        assertEquals(ReducedMotionPreference.SYSTEM, ReducedMotionPreference.fromLegacyReducedMotion(false))
    }

    @Test
    fun nextCyclesThroughEveryPreference() {
        assertEquals(ReducedMotionPreference.ON, ReducedMotionPreference.SYSTEM.next())
        assertEquals(ReducedMotionPreference.OFF, ReducedMotionPreference.ON.next())
        assertEquals(ReducedMotionPreference.SYSTEM, ReducedMotionPreference.OFF.next())
    }

    @Test
    fun zeroAnimatorDurationScaleMeansSystemReducedMotion() {
        assertTrue(SystemAnimationSignals(animatorDurationScale = 0f).reducedMotion)
        assertFalse(SystemAnimationSignals(animatorDurationScale = 0.5f).reducedMotion)
        assertFalse(SystemAnimationSignals(animatorDurationScale = 1f).reducedMotion)
        assertFalse(SystemAnimationSignals().reducedMotion)
    }

    @Test
    fun systemProjectionChangesResolutionWithoutChangingStoredIntent() {
        val stored = LauncherSettings()
        val projected = stored.withSystemReducedMotion(true)

        assertTrue(projected.motion.reducedMotion)
        assertEquals(stored.motion.reducedMotionPreference, projected.motion.reducedMotionPreference)
        assertSame(stored, stored.withSystemReducedMotion(false))
    }

    @Test
    fun systemReducedMotionReachesAdaptiveStageResolutionUnlessOverriddenOff() {
        val viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200)

        assertTrue(
            LauncherSettings().withSystemReducedMotion(true).resolveAdaptiveStageCardStack(viewport).reducedMotion,
        )
        assertFalse(
            LauncherSettings(motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.OFF))
                .withSystemReducedMotion(true)
                .resolveAdaptiveStageCardStack(viewport)
                .reducedMotion,
        )
    }
}

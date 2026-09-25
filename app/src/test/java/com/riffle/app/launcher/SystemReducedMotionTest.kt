package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemReducedMotionTest {
    @Test
    fun shellProjectionResolvesSystemReducedMotionForTheDefaultPreference() {
        val stored = LauncherShellState()

        val projected = stored.withSystemReducedMotion(systemReducedMotion = true)

        assertTrue(projected.launcherSettings.motion.reducedMotion)
        assertEquals(ReducedMotionPreference.SYSTEM, projected.launcherSettings.motion.reducedMotionPreference)
        assertSame(stored, stored.withSystemReducedMotion(systemReducedMotion = false))
    }

    @Test
    fun explicitOffPreferenceIgnoresSystemReducedMotion() {
        val stored =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.OFF),
                    ),
            )

        assertFalse(stored.withSystemReducedMotion(systemReducedMotion = true).launcherSettings.motion.reducedMotion)
    }

    @Test
    fun settingsDescriptionExplainsWhenTheSystemIsReducingMotion() {
        assertNotEquals(
            reducedMotionDescription(ReducedMotionPreference.SYSTEM, systemReducedMotion = false),
            reducedMotionDescription(ReducedMotionPreference.SYSTEM, systemReducedMotion = true),
        )
        assertEquals(
            reducedMotionDescription(ReducedMotionPreference.ON, systemReducedMotion = false),
            reducedMotionDescription(ReducedMotionPreference.ON, systemReducedMotion = true),
        )
        assertEquals(
            listOf("System", "On", "Off"),
            ReducedMotionPreference.entries.map { preference -> preference.settingsLabel() },
        )
    }
}

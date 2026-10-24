package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.PrivacySettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherScreenProtectionTest {
    private fun state(
        protect: Boolean,
        destination: ShellDestination = ShellDestination.HOME,
    ) = LauncherShellState(
        launcherSettings = LauncherSettings(privacy = PrivacySettings(protectScreenContent = protect)),
        destination = destination,
    )

    @Test
    fun offByDefaultEverywhere() {
        ShellDestination.entries.forEach { destination ->
            assertFalse(launcherScreenProtected(LauncherShellState(destination = destination)))
        }
    }

    @Test
    fun whenOnEverySurfaceThatCanShowItemsIsProtectedButSettingsIsNot() {
        assertTrue(launcherScreenProtected(state(protect = true, destination = ShellDestination.HOME)))
        assertTrue(launcherScreenProtected(state(protect = true, destination = ShellDestination.APP_DRAWER)))
        assertTrue(launcherScreenProtected(state(protect = true, destination = ShellDestination.SEARCH)))
        assertTrue(launcherScreenProtected(state(protect = true, destination = ShellDestination.NOTIFICATIONS)))
        assertFalse(launcherScreenProtected(state(protect = true, destination = ShellDestination.SETTINGS)))
    }

    @Test
    fun syncAppliesOnlyChangedDecisionsInOrder() {
        val states =
            flowOf(
                state(protect = false),
                state(protect = false, destination = ShellDestination.SEARCH),
                state(protect = true, destination = ShellDestination.SETTINGS),
                state(protect = true),
                state(protect = true, destination = ShellDestination.APP_DRAWER),
                state(protect = false),
            )
        val applied = mutableListOf<Boolean>()
        val port = ScreenContentProtectionPort { applied += it }

        runBlocking { screenProtectionDecisions(states).toList().forEach(port::setProtected) }

        assertEquals(listOf(false, true, false), applied)
    }
}

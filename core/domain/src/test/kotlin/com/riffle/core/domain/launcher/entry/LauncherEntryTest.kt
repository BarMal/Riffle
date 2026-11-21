package com.riffle.core.domain.launcher.entry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherEntryTest {
    @Test
    fun aHeldHomeRoleOpensHomeAndAnythingElseOpensTheApp() {
        assertEquals(LauncherEntryTarget.HOME, launcherEntryTarget(homeRoleHeld = true))
        assertEquals(LauncherEntryTarget.OPEN_APP, launcherEntryTarget(homeRoleHeld = false))
    }

    @Test
    fun aTaskRootStartedFromANonHomeIntentWithTheRoleHeldHandsOver() {
        assertTrue(handOver(isTaskRoot = true, hasHomeCategory = false, already = false, role = true))
    }

    @Test
    fun aHomeIntentANonRootTheRoleNotHeldOrAnEarlierHandOverAllStay() {
        assertFalse(handOver(isTaskRoot = true, hasHomeCategory = true, already = false, role = true))
        assertFalse(handOver(isTaskRoot = false, hasHomeCategory = false, already = false, role = true))
        assertFalse(handOver(isTaskRoot = true, hasHomeCategory = false, already = false, role = false))
        assertFalse(handOver(isTaskRoot = true, hasHomeCategory = false, already = true, role = true))
    }

    @Test
    fun theRoleIsAskedOnlyWhenEveryCheaperConditionHolds() {
        var asked = 0
        val ask = {
            asked++
            true
        }

        shouldHandOverToHome(isTaskRoot = true, hasHomeCategory = true, alreadyHandedOver = false, homeRoleHeld = ask)
        shouldHandOverToHome(isTaskRoot = false, hasHomeCategory = false, alreadyHandedOver = false, homeRoleHeld = ask)
        shouldHandOverToHome(isTaskRoot = true, hasHomeCategory = false, alreadyHandedOver = true, homeRoleHeld = ask)
        assertEquals(0, asked)

        shouldHandOverToHome(isTaskRoot = true, hasHomeCategory = false, alreadyHandedOver = false, homeRoleHeld = ask)
        assertEquals(1, asked)
    }

    private fun handOver(
        isTaskRoot: Boolean,
        hasHomeCategory: Boolean,
        already: Boolean,
        role: Boolean,
    ) = shouldHandOverToHome(isTaskRoot, hasHomeCategory, already) { role }
}

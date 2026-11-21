package com.riffle.core.domain.launcher.entry

import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherEntryTest {
    @Test
    fun aHeldHomeRoleOpensHomeAndAnythingElseOpensTheApp() {
        assertEquals(LauncherEntryTarget.HOME, launcherEntryTarget(homeRoleHeld = true))
        assertEquals(LauncherEntryTarget.OPEN_APP, launcherEntryTarget(homeRoleHeld = false))
    }
}

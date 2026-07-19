package com.riffle.app.launcher.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAccessSettingsActionTest {
    @Test
    fun doesNotOpenUsageAccessSettingsWhenActionIsUnavailable() {
        var openCount = 0

        val opened =
            openUsageAccessSettings(
                isAvailable = { false },
                open = { openCount += 1 },
            )

        assertEquals(false, opened)
        assertEquals(0, openCount)
    }

    @Test
    fun opensUsageAccessSettingsFromAvailableAction() {
        var openCount = 0

        val opened =
            openUsageAccessSettings(
                isAvailable = { true },
                open = { openCount += 1 },
            )

        assertEquals(true, opened)
        assertEquals(1, openCount)
    }

    @Test
    fun reportsUnavailableWhenOemSettingsLaunchFails() {
        val opened =
            openUsageAccessSettings(
                isAvailable = { true },
                open = { error("OEM Settings rejected the intent") },
            )

        assertEquals(false, opened)
    }
}

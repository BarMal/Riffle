package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHomeAppStatusTest {
    @Test
    fun labelsDefaultHomeStatus() {
        assertEquals("Riffle is default", HomeRoleStatus.DEFAULT_HOME.settingsHomeAppStatusLabel())
        assertEquals("Riffle is not default", HomeRoleStatus.NOT_DEFAULT_HOME.settingsHomeAppStatusLabel())
        assertEquals("Status unknown", HomeRoleStatus.UNKNOWN.settingsHomeAppStatusLabel())
    }
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellPlatformDependenciesTest {
    @Test
    fun defaultAvailabilityHidesCardModeForEveryDeviceClass() {
        val availability = defaultLauncherViewModeAvailability()

        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            assertFalse(availability.isAvailable(deviceClass, LauncherViewMode.CARD_INTERFACE))
            assertTrue(availability.isAvailable(deviceClass, LauncherViewMode.HOME_SCREEN_LIBRARY))
        }
    }
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellPlatformDependenciesTest {
    @Test
    fun defaultAvailabilityExposesOnlyLibraryForEveryDeviceClass() {
        val availability = defaultLauncherViewModeAvailability()

        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            assertFalse(availability.isAvailable(deviceClass, LauncherViewMode.CARD_INTERFACE))
            assertFalse(availability.isAvailable(deviceClass, LauncherViewMode.STANDARD_APP_DRAWER))
            assertTrue(availability.isAvailable(deviceClass, LauncherViewMode.HOME_SCREEN_LIBRARY))
        }
    }
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultViewModeAvailabilityTest {
    private val availability = defaultLauncherViewModeAvailability()

    @Test
    fun onlyLibraryIsOffered() {
        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            assertEquals(listOf(LauncherViewMode.HOME_SCREEN_LIBRARY), availability.availableModes(deviceClass))
        }
    }

    @Test
    fun hiddenModesResolveToLibrary() {
        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            LauncherViewMode.entries.forEach { mode ->
                assertEquals(
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    availability.availableModeOrStandard(deviceClass, mode),
                )
            }
        }
    }
}

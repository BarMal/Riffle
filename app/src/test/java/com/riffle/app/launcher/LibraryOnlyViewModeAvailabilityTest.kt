package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOnlyViewModeAvailabilityTest {
    private val availability = libraryOnlyLauncherViewModeAvailability()

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

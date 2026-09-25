package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSurfaceModeSettingTest {
    @Test
    fun offersCardsThenStandardButNeverLibrary() {
        assertEquals(
            listOf(LauncherViewMode.CARD_INTERFACE, LauncherViewMode.STANDARD_APP_DRAWER),
            homeSurfaceModeOptions(LauncherViewMode.entries),
        )
    }

    @Test
    fun omitsAHomeModeTheDeviceClassCannotUse() {
        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER),
            homeSurfaceModeOptions(listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.HOME_SCREEN_LIBRARY)),
        )
    }
}

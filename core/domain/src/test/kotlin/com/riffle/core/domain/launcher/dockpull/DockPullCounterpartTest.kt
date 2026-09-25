package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeRing
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullCounterpartTest {
    @Test
    fun everyHomeModeSwitchesToLibrary() {
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            dockPullCounterpartMode(LauncherViewMode.CARD_INTERFACE, ModeRing.DEFAULT),
        )
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            dockPullCounterpartMode(LauncherViewMode.STANDARD_APP_DRAWER, ModeRing.DEFAULT),
        )
    }

    @Test
    fun librarySwitchesToTheRingsFirstHomeMode() {
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            dockPullCounterpartMode(LauncherViewMode.HOME_SCREEN_LIBRARY, ModeRing.DEFAULT),
        )
        val standardFirst =
            ModeRing(
                listOf(
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.CARD_INTERFACE,
                ),
            )
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            dockPullCounterpartMode(LauncherViewMode.HOME_SCREEN_LIBRARY, standardFirst),
        )
    }

    @Test
    fun aLayoutSetUsesItsDeviceClassRing() {
        val standard = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.STANDARD_APP_DRAWER)
        // A device on Standard with no configured ring falls back to Standard, Library, Cards.
        val set = HomeLayoutSet.fromLayout(standard).selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            set.dockPullCounterpartMode(HomeLayoutDeviceClass.PHONE, LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            set.dockPullCounterpartMode(HomeLayoutDeviceClass.PHONE, LauncherViewMode.STANDARD_APP_DRAWER),
        )
    }
}

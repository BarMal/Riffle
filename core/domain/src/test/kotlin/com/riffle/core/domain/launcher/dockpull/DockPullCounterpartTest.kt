package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.withHomeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullCounterpartTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun everyHomeModeSwitchesToLibrary() {
        listOf(LauncherViewMode.CARD_INTERFACE, LauncherViewMode.STANDARD_APP_DRAWER).forEach { home ->
            val set = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard().copy(viewMode = home))

            assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, set.dockPullCounterpartMode(phone, home))
        }
    }

    @Test
    fun librarySwitchesToThePairsHomeMode() {
        val onLibrary =
            HomeLayoutSet.fromLayout(
                HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )

        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            onLibrary.dockPullCounterpartMode(phone, LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            onLibrary
                .withHomeMode(deviceClass = phone, mode = LauncherViewMode.STANDARD_APP_DRAWER)
                .dockPullCounterpartMode(phone, LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
    }
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInsetPolicyTest {
    @Test
    fun defaultAppearanceReservesSafeDrawingForBothHomeBars() {
        assertEquals(
            HomeInsetPolicy(
                reserveStatusBar = true,
                reserveNavigationBar = true,
            ),
            homeInsetPolicy(AppearanceSettings()),
        )
    }

    @Test
    fun fullscreenHomeDoesNotReserveSafeDrawingForEitherHomeBar() {
        assertEquals(
            HomeInsetPolicy(
                reserveStatusBar = false,
                reserveNavigationBar = false,
            ),
            homeInsetPolicy(AppearanceSettings(fullscreenHome = true)),
        )
    }

    @Test
    fun hiddenHomeStatusBarDoesNotReserveStatusBarSafeDrawingOnly() {
        assertEquals(
            HomeInsetPolicy(
                reserveStatusBar = false,
                reserveNavigationBar = true,
            ),
            homeInsetPolicy(AppearanceSettings(hideStatusBarOnHome = true)),
        )
    }

    @Test
    fun hiddenHomeNavigationBarDoesNotReserveNavigationBarSafeDrawingOnly() {
        assertEquals(
            HomeInsetPolicy(
                reserveStatusBar = true,
                reserveNavigationBar = false,
            ),
            homeInsetPolicy(AppearanceSettings(hideNavigationBarOnHome = true)),
        )
    }
}

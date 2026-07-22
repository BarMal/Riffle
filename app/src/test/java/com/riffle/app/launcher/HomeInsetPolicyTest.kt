package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInsetPolicyTest {
    @Test
    fun nonNegativeGridInsetsPreserveConfiguredEdgesAndRejectNegativeValues() {
        assertEquals(
            GridInsets(start = 12, top = 16, end = 20, bottom = 24),
            GridInsets(start = 12, top = 16, end = 20, bottom = 24).nonNegative(),
        )
        assertEquals(
            GridInsets(start = 0, top = 0, end = 0, bottom = 0),
            GridInsets(start = -1, top = -2, end = -3, bottom = -4).nonNegative(),
        )
    }

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

    @Test
    fun typedHomeSystemBarsDriveInsetReservation() {
        assertEquals(
            HomeInsetPolicy(
                reserveStatusBar = false,
                reserveNavigationBar = true,
            ),
            homeInsetPolicy(
                AppearanceSettings().withHomeSystemBars(
                    HomeSystemBars(
                        hideStatusBarOnHome = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun dockShelfGestureDoesNotReserveBottomSystemGestureZoneWhenNavigationBarIsReserved() {
        assertEquals(
            DockShelfGesturePolicy(
                enabled = true,
                bottomSystemGestureExclusionDp = 0,
            ),
            dockShelfGesturePolicy(
                isDockVisible = true,
            ),
        )
    }

    @Test
    fun dockShelfGestureDoesNotReserveBottomSystemGestureZoneWhenNavigationBarIsHidden() {
        assertEquals(
            DockShelfGesturePolicy(
                enabled = true,
                bottomSystemGestureExclusionDp = 0,
            ),
            dockShelfGesturePolicy(
                isDockVisible = true,
            ),
        )
    }

    @Test
    fun dockShelfGestureDisablesWhenDockIsHidden() {
        assertEquals(
            DockShelfGesturePolicy(
                enabled = false,
                bottomSystemGestureExclusionDp = 0,
            ),
            dockShelfGesturePolicy(
                isDockVisible = false,
            ),
        )
    }
}

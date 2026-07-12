package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInsetPolicyTest {
    @Test
    fun homeSurfaceUsesConfiguredNonNegativeGridInsets() {
        val insets = GridInsets(start = 8, top = -12, end = 16, bottom = 20)

        assertEquals(
            GridInsets(start = 8, top = 0, end = 16, bottom = 20),
            homeSurfaceMargins(insets),
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
                homeInsetPolicy = HomeInsetPolicy(reserveNavigationBar = true),
            ),
        )
    }

    @Test
    fun dockShelfGestureReservesBottomSystemGestureZoneWhenNavigationBarIsNotReserved() {
        assertEquals(
            DockShelfGesturePolicy(
                enabled = true,
                bottomSystemGestureExclusionDp = 24,
            ),
            dockShelfGesturePolicy(
                isDockVisible = true,
                homeInsetPolicy = HomeInsetPolicy(reserveNavigationBar = false),
            ),
        )
    }

    @Test
    fun dockShelfGestureDisablesWhenDockIsHidden() {
        assertEquals(
            DockShelfGesturePolicy(
                enabled = false,
                bottomSystemGestureExclusionDp = 24,
            ),
            dockShelfGesturePolicy(
                isDockVisible = false,
                homeInsetPolicy = HomeInsetPolicy(reserveNavigationBar = false),
            ),
        )
    }
}

package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceSettingsPolicyTest {
    @Test
    fun eachBarIsIndependent() {
        val appearance = AppearanceSettings().withHomeStatusBarHidden(hidden = true)

        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(false, appearance.homeNavigationBarHidden)
    }

    @Test
    fun hidingBothBarsIsTheFullscreenAction() {
        val appearance = AppearanceSettings(hideStatusBarOnHome = true).withFullscreenHome(enabled = true)

        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(true, appearance.homeNavigationBarHidden)
    }

    @Test
    fun showingBothBarsClearsBoth() {
        val appearance =
            AppearanceSettings(hideStatusBarOnHome = true, hideNavigationBarOnHome = true)
                .withFullscreenHome(enabled = false)

        assertEquals(false, appearance.homeStatusBarHidden)
        assertEquals(false, appearance.homeNavigationBarHidden)
    }

    @Test
    fun clearingOneBarKeepsTheOther() {
        val appearance =
            AppearanceSettings(hideStatusBarOnHome = true, hideNavigationBarOnHome = true)
                .withHomeNavigationBarHidden(hidden = false)

        assertEquals(true, appearance.hideStatusBarOnHome)
        assertEquals(false, appearance.hideNavigationBarOnHome)
    }

    @Test
    fun appearanceExposesTypedHomeSystemBars() {
        val appearance = AppearanceSettings(hideStatusBarOnHome = true)

        assertEquals(HomeSystemBars(hideStatusBarOnHome = true), appearance.homeSystemBars)
    }
}

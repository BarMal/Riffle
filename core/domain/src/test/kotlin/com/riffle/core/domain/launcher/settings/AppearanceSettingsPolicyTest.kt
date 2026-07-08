package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceSettingsPolicyTest {
    @Test
    fun fullscreenHomeSelectionPreservesIndependentSystemBarSettings() {
        val appearance =
            AppearanceSettings(hideStatusBarOnHome = true)
                .withFullscreenHome(enabled = true)

        assertEquals(true, appearance.fullscreenHome)
        assertEquals(true, appearance.hideStatusBarOnHome)
        assertEquals(false, appearance.hideNavigationBarOnHome)
        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(true, appearance.homeNavigationBarHidden)
    }

    @Test
    fun fullscreenHomeClearingShowsBothSystemBars() {
        val appearance =
            AppearanceSettings(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = true,
            ).withFullscreenHome(enabled = false)

        assertEquals(false, appearance.fullscreenHome)
        assertEquals(false, appearance.hideStatusBarOnHome)
        assertEquals(false, appearance.hideNavigationBarOnHome)
        assertEquals(false, appearance.homeStatusBarHidden)
        assertEquals(false, appearance.homeNavigationBarHidden)
    }

    @Test
    fun fullscreenHomeClearingRestoresIndependentSystemBarSelection() {
        val appearance =
            AppearanceSettings(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
            ).withFullscreenHome(enabled = false)

        assertEquals(false, appearance.fullscreenHome)
        assertEquals(true, appearance.hideStatusBarOnHome)
        assertEquals(false, appearance.hideNavigationBarOnHome)
        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(false, appearance.homeNavigationBarHidden)
    }

    @Test
    fun hidingBothIndependentSystemBarsEnablesFullscreenHome() {
        val appearance =
            AppearanceSettings(hideNavigationBarOnHome = true)
                .withHomeStatusBarHidden(hidden = true)

        assertEquals(true, appearance.fullscreenHome)
        assertEquals(true, appearance.hideStatusBarOnHome)
        assertEquals(true, appearance.hideNavigationBarOnHome)
        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(true, appearance.homeNavigationBarHidden)
    }

    @Test
    fun clearingOneSystemBarClearsFullscreenHomeAndPreservesOtherEffectiveBar() {
        val appearance =
            AppearanceSettings(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = true,
            ).withHomeNavigationBarHidden(hidden = false)

        assertEquals(false, appearance.fullscreenHome)
        assertEquals(true, appearance.hideStatusBarOnHome)
        assertEquals(false, appearance.hideNavigationBarOnHome)
        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(false, appearance.homeNavigationBarHidden)
    }

    @Test
    fun effectiveSystemBarStateIncludesFullscreenHome() {
        val appearance = AppearanceSettings(fullscreenHome = true)

        assertEquals(true, appearance.homeStatusBarHidden)
        assertEquals(true, appearance.homeNavigationBarHidden)
    }
}

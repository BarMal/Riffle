package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSystemUiSyncTest {
    @Test
    fun fullscreenHomeHidesBothSystemBarsOnHome() {
        val mode =
            launcherSystemUiMode(
                LauncherShellState(
                    launcherSettings =
                        LauncherSettings(
                            appearance = AppearanceSettings(fullscreenHome = true),
                        ),
                ),
            )

        assertTrue(mode.shouldHideStatusBars)
        assertTrue(mode.shouldHideNavigationBars)
    }

    @Test
    fun independentHomeStatusBarSettingOnlyHidesStatusBarOnHome() {
        val mode =
            launcherSystemUiMode(
                LauncherShellState(
                    launcherSettings =
                        LauncherSettings(
                            appearance = AppearanceSettings(hideStatusBarOnHome = true),
                        ),
                ),
            )

        assertTrue(mode.shouldHideStatusBars)
        assertFalse(mode.shouldHideNavigationBars)
    }

    @Test
    fun homeSystemBarSettingsDoNotHideBarsOutsideHome() {
        val mode =
            launcherSystemUiMode(
                LauncherShellState(
                    destination = ShellDestination.SETTINGS,
                    launcherSettings =
                        LauncherSettings(
                            appearance =
                                AppearanceSettings(
                                    hideStatusBarOnHome = true,
                                    hideNavigationBarOnHome = true,
                                ),
                        ),
                ),
            )

        assertFalse(mode.shouldHideStatusBars)
        assertFalse(mode.shouldHideNavigationBars)
    }

    @Test
    fun createsDistinctModesForIndependentSystemBarSelections() {
        val statusOnly =
            LauncherSystemUiMode(
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
                destination = ShellDestination.HOME,
            )
        val navigationOnly =
            LauncherSystemUiMode(
                hideStatusBarOnHome = false,
                hideNavigationBarOnHome = true,
                destination = ShellDestination.HOME,
            )

        assertEquals(false, statusOnly == navigationOnly)
    }
}

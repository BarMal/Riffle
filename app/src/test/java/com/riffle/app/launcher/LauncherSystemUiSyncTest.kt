package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
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
                themeMode = LauncherThemeMode.SYSTEM,
            )
        val navigationOnly =
            LauncherSystemUiMode(
                hideStatusBarOnHome = false,
                hideNavigationBarOnHome = true,
                destination = ShellDestination.HOME,
                themeMode = LauncherThemeMode.SYSTEM,
            )

        assertEquals(false, statusOnly == navigationOnly)
    }

    @Test
    fun fullscreenHomeProducesSameModeForLegacyAndPreservedBarSelections() {
        val preservedPreferenceMode =
            launcherSystemUiMode(
                LauncherShellState(
                    launcherSettings =
                        LauncherSettings(
                            appearance = AppearanceSettings(fullscreenHome = true),
                        ),
                ),
            )
        val legacyMode =
            launcherSystemUiMode(
                LauncherShellState(
                    launcherSettings =
                        LauncherSettings(
                            appearance =
                                AppearanceSettings(
                                    fullscreenHome = true,
                                    hideStatusBarOnHome = true,
                                    hideNavigationBarOnHome = true,
                                ),
                        ),
                ),
            )

        assertEquals(legacyMode, preservedPreferenceMode)
    }

    @Test
    fun typedHomeSystemBarsProduceEffectiveSystemUiMode() {
        val appearance =
            AppearanceSettings().withHomeSystemBars(
                HomeSystemBars(
                    hideStatusBarOnHome = true,
                    hideNavigationBarOnHome = false,
                ),
            )
        val mode =
            launcherSystemUiMode(
                LauncherShellState(
                    launcherSettings =
                        LauncherSettings(
                            appearance = appearance,
                        ),
                ),
            )

        assertTrue(mode.shouldHideStatusBars)
        assertFalse(mode.shouldHideNavigationBars)
    }

    @Test
    fun lightThemeUsesDarkStatusBarIcons() {
        val mode = launcherSystemUiMode(shellStateWithTheme(LauncherThemeMode.LIGHT))

        assertTrue(mode.usesLightStatusBarAppearance(systemIsDarkTheme = true))
    }

    @Test
    fun darkThemeUsesLightStatusBarIcons() {
        val mode = launcherSystemUiMode(shellStateWithTheme(LauncherThemeMode.DARK))

        assertFalse(mode.usesLightStatusBarAppearance(systemIsDarkTheme = false))
    }

    @Test
    fun systemThemeFollowsSystemDarkModeForStatusBarIcons() {
        val mode = launcherSystemUiMode(shellStateWithTheme(LauncherThemeMode.SYSTEM))

        assertTrue(mode.usesLightStatusBarAppearance(systemIsDarkTheme = false))
        assertFalse(mode.usesLightStatusBarAppearance(systemIsDarkTheme = true))
    }

    private fun shellStateWithTheme(themeMode: LauncherThemeMode): LauncherShellState =
        LauncherShellState(
            launcherSettings =
                LauncherSettings(
                    appearance = AppearanceSettings(themeMode = themeMode),
                ),
        )
}

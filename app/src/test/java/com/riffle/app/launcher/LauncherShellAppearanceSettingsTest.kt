package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellAppearanceSettingsTest {
    @Test
    fun savesIndependentHomeSystemBarSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectHomeStatusBarHidden(hidden = true),
        )

        assertFalse(viewModel.state.value.launcherSettings.appearance.fullscreenHome)
        assertTrue(viewModel.state.value.launcherSettings.appearance.hideStatusBarOnHome)
        assertFalse(viewModel.state.value.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesWallpaperScrollModeSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.SCROLLING),
        )

        assertEquals(
            WallpaperScrollMode.SCROLLING,
            viewModel.state.value.launcherSettings.appearance.wallpaper.scrollMode,
        )
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesThemeTokenOverrides() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectLauncherThemeCornerStyle(LauncherThemeCornerStyle.ROUNDED),
        )
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectLauncherThemeTypography(LauncherThemeTypography.MONOSPACE),
        )

        assertEquals(
            LauncherThemeCornerStyle.ROUNDED,
            viewModel.state.value.launcherSettings.appearance.themeCornerStyle,
        )
        assertEquals(
            LauncherThemeTypography.MONOSPACE,
            viewModel.state.value.launcherSettings.appearance.themeTypography,
        )
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeLauncherSettingsRepository(
        var savedSettings: LauncherSettings? = null,
    ) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }
}

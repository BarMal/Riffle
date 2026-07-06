package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
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

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellOverlayDockSettingsTest {
    @Test
    fun savesOverlayDockEnabledSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockEnabled(enabled = true),
        )

        assertEquals(true, viewModel.state.value.launcherSettings.overlayDock.enabled)
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

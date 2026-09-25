package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellMotionSettingsTest {
    @Test
    fun savesReducedMotionSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectReducedMotionPreference(ReducedMotionPreference.ON),
        )

        assertEquals(
            ReducedMotionPreference.ON,
            viewModel.state.value.launcherSettings.motion.reducedMotionPreference,
        )
        assertTrue(viewModel.state.value.launcherSettings.motion.reducedMotion)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesMotionPerformanceTargetSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectMotionPerformanceTargetFps(MotionPerformanceTargetFps.FPS_90),
        )

        assertEquals(
            MotionPerformanceTargetFps.FPS_90,
            viewModel.state.value.launcherSettings.motion.performanceTargetFps,
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

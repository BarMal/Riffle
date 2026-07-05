package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellGestureSettingsTest {
    @Test
    fun savesHomeSwipeGestureActionSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectHomeSwipeGestureAction(
                direction = HomeSwipeGestureDirection.UP,
                action = LauncherGestureAction.OPEN_SEARCH,
            ),
        )

        assertEquals(LauncherGestureAction.OPEN_SEARCH, viewModel.state.value.launcherSettings.gestures.homeSwipe.up)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesHomeGestureActionSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectHomeGestureAction(
                gesture = HomeGesture.PINCH_IN,
                action = LauncherGestureAction.OPEN_SETTINGS,
            ),
        )

        assertEquals(
            LauncherGestureAction.OPEN_SETTINGS,
            viewModel.state.value.launcherSettings.gestures.homeGestures.actionFor(HomeGesture.PINCH_IN),
        )
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun resetsHomeSwipeGestureActionsToDefaults() {
        val repository =
            FakeLauncherSettingsRepository(
                savedSettings =
                    LauncherSettings(
                        gestures =
                            GestureSettings(
                                homeGestures =
                                    HomeGestureSettings(
                                        actions =
                                            mapOf(
                                                HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                                                HomeGesture.PINCH_IN to LauncherGestureAction.OPEN_SETTINGS,
                                            ),
                                    ),
                            ),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.ResetHomeSwipeGestureActions)

        assertEquals(HomeGestureSettings(), viewModel.state.value.launcherSettings.gestures.homeGestures)
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

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherSettingsStateReducerTest {
    @Test
    fun appliesSettingsStateActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHapticFeedbackStrength(HapticFeedbackStrength.STRONG),
            )

        assertEquals(HapticFeedbackStrength.STRONG, updatedState.launcherSettings.haptics.feedbackStrength)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun contextualSettingsDefaultOff() {
        val state = LauncherShellState()

        assertEquals(false, state.launcherSettings.contextual.enabled)
    }

    @Test
    fun enablesContextualSettingsAndPersistsSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val updatedState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectContextualEnabled(enabled = true),
            )

        assertEquals(true, updatedState.launcherSettings.contextual.enabled)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun disablesContextualSettingsAndPersistsSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val enabledState =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        contextual = ContextualSettings(enabled = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = enabledState,
                action = LauncherShellAction.SelectContextualEnabled(enabled = false),
            )

        assertEquals(false, updatedState.launcherSettings.contextual.enabled)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun fullscreenHomeSelectionPreservesIndependentSystemBarSettings() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideStatusBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = true),
            )

        assertEquals(true, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun fullscreenHomeClearingRestoresIndependentSystemBarSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                fullscreenHome = true,
                                hideStatusBarOnHome = true,
                            ),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = false),
            )

        assertEquals(false, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun independentSystemBarSelectionUpdatesFullscreenHomeWhenBothAreHidden() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideNavigationBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHomeStatusBarHidden(hidden = true),
            )

        assertEquals(true, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun independentSystemBarSelectionClearsFullscreenHomeWhenOneBarIsVisible() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
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
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHomeNavigationBarHidden(hidden = false),
            )

        assertEquals(false, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun ignoresSettingsSideEffectActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.RequestNotificationAccess,
            )

        assertSame(state, updatedState)
        assertEquals(null, repository.savedSettings)
    }

    @Test
    fun ignoresNonSettingsActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.RefreshInstalledApps,
            )

        assertSame(state, updatedState)
        assertEquals(null, repository.savedSettings)
    }

    private fun reducer(
        launcherSettingsRepository: LauncherSettingsRepository = FakeLauncherSettingsRepository(),
    ): LauncherSettingsStateReducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository = FakeHomeLayoutRepository(),
            launcherSettingsRepository = launcherSettingsRepository,
            appVisibilityRepository = FakeAppVisibilityRepository(),
        )

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private class FakeLauncherSettingsRepository(
        var savedSettings: LauncherSettings? = null,
    ) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }

    private class FakeAppVisibilityRepository : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }
}

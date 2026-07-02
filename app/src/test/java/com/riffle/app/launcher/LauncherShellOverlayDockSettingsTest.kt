package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
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

    @Test
    fun savesOverlayDockHandleSettings() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.START))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 96))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -48))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 65))

        val settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(OverlayDockEdge.START, settings.edge)
        assertEquals(96, settings.handleHeightDp)
        assertEquals(-48, settings.verticalOffsetDp)
        assertEquals(65, settings.handleAlphaPercent)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun clampsOverlayDockHandleSettings() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = -1))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = -1))

        var settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP, settings.handleHeightDp)
        assertEquals(MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP, settings.verticalOffsetDp)
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, settings.handleAlphaPercent)

        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = 999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 999))

        settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP, settings.handleHeightDp)
        assertEquals(MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP, settings.verticalOffsetDp)
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, settings.handleAlphaPercent)
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

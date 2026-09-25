package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModePair

/**
 * The "Home screen" choice (#1241): which mode is the Home side of the device class's fixed
 * Home ↔ Library pair. Library is always the other side, reached by pulling the dock, so it is not
 * offered here.
 */
@Composable
internal fun HomeSurfaceModeSetting(
    pair: ModePair,
    availableViewModes: List<LauncherViewMode>,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Home screen",
        subtitle = "Pull the dock to move between ${pair.home.label} and Library.",
        options = homeSurfaceModeOptions(availableViewModes),
        selected = pair.home,
        onSelect = { mode -> onAction(LauncherShellAction.SelectHomeSurfaceMode(mode)) },
        label = { mode -> mode.label },
    )
}

/** The Home modes the device class can use, Cards first; never Library. */
internal fun homeSurfaceModeOptions(availableViewModes: List<LauncherViewMode>): List<LauncherViewMode> =
    ModePair.HOME_MODES.filter { mode -> mode in availableViewModes }

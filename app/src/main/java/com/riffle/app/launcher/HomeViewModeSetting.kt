package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.home.LauncherViewMode

@Composable
internal fun HomeViewModeSetting(
    viewMode: LauncherViewMode,
    availableViewModes: List<LauncherViewMode>,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Mode",
        subtitle = viewMode.label,
        options = availableViewModes,
        selected = viewMode,
        onSelect = { mode -> onAction(LauncherShellAction.SelectLauncherViewMode(mode)) },
        label = { mode -> mode.shortLabel },
    )
}

private val LauncherViewMode.label: String
    get() =
        when (this) {
            LauncherViewMode.STANDARD_APP_DRAWER -> "Standard"
            LauncherViewMode.HOME_SCREEN_LIBRARY -> "Library"
            LauncherViewMode.CARD_INTERFACE -> "Cards"
        }

private val LauncherViewMode.shortLabel: String
    get() =
        when (this) {
            LauncherViewMode.STANDARD_APP_DRAWER -> "Std"
            LauncherViewMode.HOME_SCREEN_LIBRARY -> "Lib"
            LauncherViewMode.CARD_INTERFACE -> "Cards"
        }

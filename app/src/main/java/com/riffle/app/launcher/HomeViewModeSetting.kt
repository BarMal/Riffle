package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.LauncherViewMode

@Composable
internal fun HomeViewModeSetting(
    viewMode: LauncherViewMode,
    availableViewModes: List<LauncherViewMode>,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Mode",
            subtitle = viewMode.label,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableViewModes.forEach { mode ->
                TextButton(
                    enabled = mode != viewMode,
                    onClick = { onAction(LauncherShellAction.SelectLauncherViewMode(mode)) },
                ) {
                    SettingsButtonText(text = mode.shortLabel)
                }
            }
        }
    }
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

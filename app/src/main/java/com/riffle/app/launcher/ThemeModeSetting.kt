package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset

@Composable
internal fun ThemeModeSetting(
    selectedMode: LauncherThemeMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Color mode",
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LauncherThemeMode.entries.forEach { mode ->
                    TextButton(
                        enabled = mode != selectedMode,
                        onClick = { onAction(LauncherShellAction.SelectLauncherThemeMode(mode)) },
                    ) {
                        SettingsButtonText(text = mode.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
        },
    )
}

@Composable
internal fun ThemePresetSetting(
    selectedPreset: LauncherThemePreset,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Theme preset",
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LauncherThemePreset.entries.forEach { preset ->
                    TextButton(
                        enabled = preset != selectedPreset,
                        onClick = { onAction(LauncherShellAction.SelectLauncherThemePreset(preset)) },
                    ) {
                        SettingsButtonText(text = preset.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
        },
    )
}

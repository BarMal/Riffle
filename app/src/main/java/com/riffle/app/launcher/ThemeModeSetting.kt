package com.riffle.app.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
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
    Column {
        SettingsListRow(title = "Theme preset")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LauncherThemePreset.entries.forEach { preset ->
                TextButton(
                    enabled = preset != selectedPreset,
                    onClick = { onAction(LauncherShellAction.SelectLauncherThemePreset(preset)) },
                ) {
                    SettingsButtonText(text = preset.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
    }
}

@Composable
internal fun ThemeAccentSetting(
    selectedAccent: LauncherThemeAccent,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column {
        SettingsListRow(title = "Theme accent")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LauncherThemeAccent.entries.forEach { accent ->
                TextButton(
                    enabled = accent != selectedAccent,
                    onClick = { onAction(LauncherShellAction.SelectLauncherThemeAccent(accent)) },
                ) {
                    SettingsButtonText(text = accent.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
    }
}

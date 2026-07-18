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
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography

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
                        SettingsButtonText(
                            text = themeOptionLabel(mode.name, isSelected = mode == selectedMode),
                        )
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
                    SettingsButtonText(
                        text = themeOptionLabel(preset.name, isSelected = preset == selectedPreset),
                    )
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
                    SettingsButtonText(
                        text = themeOptionLabel(accent.name, isSelected = accent == selectedAccent),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThemeCornerStyleSetting(
    selectedStyle: LauncherThemeCornerStyle,
    onAction: (LauncherShellAction) -> Unit,
) {
    ThemeOptionRow(title = "Card corners") {
        LauncherThemeCornerStyle.entries.forEach { style ->
            TextButton(
                enabled = style != selectedStyle,
                onClick = { onAction(LauncherShellAction.SelectLauncherThemeCornerStyle(style)) },
            ) {
                SettingsButtonText(text = themeOptionLabel(style.name, isSelected = style == selectedStyle))
            }
        }
    }
}

@Composable
internal fun ThemeTypographySetting(
    selectedTypography: LauncherThemeTypography,
    onAction: (LauncherShellAction) -> Unit,
) {
    ThemeOptionRow(title = "Typography") {
        LauncherThemeTypography.entries.forEach { typography ->
            TextButton(
                enabled = typography != selectedTypography,
                onClick = { onAction(LauncherShellAction.SelectLauncherThemeTypography(typography)) },
            ) {
                SettingsButtonText(
                    text = themeOptionLabel(typography.name, isSelected = typography == selectedTypography),
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    options: @Composable () -> Unit,
) {
    Column {
        SettingsListRow(
            title = title,
            subtitle = "Override the selected theme preset",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { options() },
        )
    }
}

internal fun themeOptionLabel(
    optionName: String,
    isSelected: Boolean,
): String =
    optionName.lowercase().replaceFirstChar(Char::uppercase).let { label ->
        if (isSelected) "$label (selected)" else label
    }

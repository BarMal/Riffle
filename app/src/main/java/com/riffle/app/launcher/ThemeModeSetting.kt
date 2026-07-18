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
import com.riffle.core.domain.launcher.settings.CustomThemeSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.MAX_CUSTOM_THEME_CARD_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MIN_CUSTOM_THEME_CARD_CORNER_RADIUS_DP

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
    customTheme: CustomThemeSettings,
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
        if (selectedPreset == LauncherThemePreset.CUSTOM) {
            DiscreteSettingSlider(
                title = "Card corner radius",
                value = customTheme.cardCornerRadiusDp,
                valueRange = MIN_CUSTOM_THEME_CARD_CORNER_RADIUS_DP..MAX_CUSTOM_THEME_CARD_CORNER_RADIUS_DP,
                valueLabel = { value -> "$value dp" },
                onValueChange = { radius ->
                    onAction(LauncherShellAction.SelectCustomThemeCardCornerRadius(radius))
                },
            )
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

internal fun themeOptionLabel(
    optionName: String,
    isSelected: Boolean,
): String =
    optionName.lowercase().replaceFirstChar(Char::uppercase).let { label ->
        if (isSelected) "$label (selected)" else label
    }

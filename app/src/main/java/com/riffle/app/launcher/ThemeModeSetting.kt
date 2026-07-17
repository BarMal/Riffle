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
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    SettingsButtonText(text = preset.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
        if (selectedPreset == LauncherThemePreset.CUSTOM) {
            CustomThemeSettingsControls(customTheme = customTheme, onAction = onAction)
        }
    }
}

@Composable
private fun CustomThemeSettingsControls(
    customTheme: CustomThemeSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Accent",
        trailingContent = {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LauncherThemeAccent.entries.forEach { accent ->
                    TextButton(
                        enabled = accent != customTheme.accent,
                        onClick = { onAction(LauncherShellAction.SelectCustomThemeAccent(accent)) },
                    ) {
                        SettingsButtonText(text = accent.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
        },
    )
    DiscreteSettingSlider(
        title = "Card corner radius",
        value = customTheme.cardCornerRadiusDp,
        valueRange = MIN_CUSTOM_THEME_CARD_CORNER_RADIUS_DP..MAX_CUSTOM_THEME_CARD_CORNER_RADIUS_DP,
        valueLabel = { value -> "$value dp" },
        onValueChange = { radius -> onAction(LauncherShellAction.SelectCustomThemeCardCornerRadius(radius)) },
    )
}

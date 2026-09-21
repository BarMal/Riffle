package com.riffle.app.launcher

import androidx.compose.runtime.Composable
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
    SettingsChoiceRow(
        title = "Color mode",
        options = LauncherThemeMode.entries,
        selected = selectedMode,
        onSelect = { mode -> onAction(LauncherShellAction.SelectLauncherThemeMode(mode)) },
        label = { mode -> themeOptionLabel(mode.name, isSelected = false) },
    )
}

@Composable
internal fun ThemePresetSetting(
    selectedPreset: LauncherThemePreset,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Theme preset",
        options = LauncherThemePreset.entries,
        selected = selectedPreset,
        onSelect = { preset -> onAction(LauncherShellAction.SelectLauncherThemePreset(preset)) },
        label = { preset -> themeOptionLabel(preset.name, isSelected = false) },
    )
}

@Composable
internal fun ThemeAccentSetting(
    selectedAccent: LauncherThemeAccent,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Theme accent",
        options = LauncherThemeAccent.entries,
        selected = selectedAccent,
        onSelect = { accent -> onAction(LauncherShellAction.SelectLauncherThemeAccent(accent)) },
        label = { accent -> themeOptionLabel(accent.name, isSelected = false) },
    )
}

@Composable
internal fun ThemeCornerStyleSetting(
    selectedStyle: LauncherThemeCornerStyle,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Card corners",
        subtitle = "Override the selected theme preset",
        options = LauncherThemeCornerStyle.entries,
        selected = selectedStyle,
        onSelect = { style -> onAction(LauncherShellAction.SelectLauncherThemeCornerStyle(style)) },
        label = { style -> themeOptionLabel(style.name, isSelected = false) },
    )
}

@Composable
internal fun ThemeTypographySetting(
    selectedTypography: LauncherThemeTypography,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Typography",
        subtitle = "Override the selected theme preset",
        options = LauncherThemeTypography.entries,
        selected = selectedTypography,
        onSelect = { typography -> onAction(LauncherShellAction.SelectLauncherThemeTypography(typography)) },
        label = { typography -> themeOptionLabel(typography.name, isSelected = false) },
    )
}

internal fun themeOptionLabel(
    optionName: String,
    isSelected: Boolean,
): String =
    optionName.lowercase().replaceFirstChar(Char::uppercase).let { label ->
        if (isSelected) "$label (selected)" else label
    }

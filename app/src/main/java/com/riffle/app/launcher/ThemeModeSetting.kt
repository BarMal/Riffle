package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography

@Composable
internal fun ThemeModeSetting(
    selectedMode: LauncherThemeMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_COLOUR_MODE),
        title = SettingKey.APPEARANCE_COLOUR_MODE.label(),
        options = LauncherThemeMode.entries,
        selected = selectedMode,
        onSelect = { mode -> onAction(LauncherShellAction.SelectLauncherThemeMode(mode)) },
        label = { mode -> themeOptionLabel(mode.name, isSelected = false) },
    )
}

@Composable
internal fun ThemeAccentSetting(
    selectedAccent: LauncherThemeAccent,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_ACCENT),
        title = SettingKey.APPEARANCE_ACCENT.label(),
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
        modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_CORNERS),
        title = SettingKey.APPEARANCE_CORNERS.label(),
        subtitle = stringResource(R.string.settings_corners_subtitle),
        options = LauncherThemeCornerStyle.entries,
        selected = selectedStyle,
        onSelect = { style -> onAction(LauncherShellAction.SelectLauncherThemeCornerStyle(style)) },
        label = { style -> cornerStyleLabel(style) },
    )
}

@Composable
internal fun ThemeTypographySetting(
    selectedTypography: LauncherThemeTypography,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_TYPOGRAPHY),
        title = SettingKey.APPEARANCE_TYPOGRAPHY.label(),
        options = LauncherThemeTypography.offered,
        selected = selectedTypography.canonical,
        onSelect = { typography -> onAction(LauncherShellAction.SelectLauncherThemeTypography(typography)) },
        label = { typography -> typographyLabel(typography) },
    )
}

/** "Preset" is the standard corner choice; Settings calls it "Standard". */
internal fun cornerStyleLabel(style: LauncherThemeCornerStyle): String =
    if (style == LauncherThemeCornerStyle.PRESET) "Standard" else themeOptionLabel(style.name, isSelected = false)

/** "Preset" is the standard typeface; the old "System" choice was the same and is no longer offered. */
internal fun typographyLabel(typography: LauncherThemeTypography): String =
    if (typography == LauncherThemeTypography.PRESET) {
        "Standard"
    } else {
        themeOptionLabel(typography.name, isSelected = false)
    }

internal fun themeOptionLabel(
    optionName: String,
    isSelected: Boolean,
): String =
    optionName.lowercase().replaceFirstChar(Char::uppercase).let { label ->
        if (isSelected) "$label (selected)" else label
    }

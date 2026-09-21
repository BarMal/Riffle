package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSource

@Composable
internal fun WallpaperScrollModeSetting(
    selectedSource: WallpaperSource,
    selectedMode: WallpaperScrollMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    val state = wallpaperScrollModeSettingState(selectedSource)

    SettingsChoiceRow(
        title = "Wallpaper motion",
        subtitle = state.subtitle,
        enabled = state.enabled,
        options = WallpaperScrollMode.entries,
        selected = selectedMode,
        onSelect = { mode -> onAction(LauncherShellAction.SelectWallpaperScrollMode(mode)) },
        label = { mode -> if (mode == WallpaperScrollMode.STATIC) "Static" else "Scroll" },
    )
}

internal fun wallpaperScrollModeSettingState(source: WallpaperSource): WallpaperScrollModeSettingState =
    if (source == WallpaperSource.SYSTEM) {
        WallpaperScrollModeSettingState(
            enabled = true,
            subtitle = "Move system wallpaper between home pages",
        )
    } else {
        WallpaperScrollModeSettingState(
            enabled = false,
            subtitle = "Available when using system wallpaper",
        )
    }

internal data class WallpaperScrollModeSettingState(
    val enabled: Boolean,
    val subtitle: String,
)

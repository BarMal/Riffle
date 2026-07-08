package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSource

@Composable
internal fun WallpaperScrollModeSetting(
    selectedSource: WallpaperSource,
    selectedMode: WallpaperScrollMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    val state = wallpaperScrollModeSettingState(selectedSource)

    SettingsListRow(
        title = "Wallpaper motion",
        subtitle = state.subtitle,
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = state.enabled && WallpaperScrollMode.STATIC != selectedMode,
                    onClick = { onAction(LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.STATIC)) },
                ) {
                    SettingsButtonText(text = "Static")
                }
                TextButton(
                    enabled = state.enabled && WallpaperScrollMode.SCROLLING != selectedMode,
                    onClick = {
                        onAction(LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.SCROLLING))
                    },
                ) {
                    SettingsButtonText(text = "Scroll")
                }
            }
        },
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

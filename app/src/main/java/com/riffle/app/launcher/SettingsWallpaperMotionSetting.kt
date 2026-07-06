package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.WallpaperScrollMode

@Composable
internal fun WallpaperScrollModeSetting(
    selectedMode: WallpaperScrollMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Wallpaper motion",
        subtitle = "Move wallpaper between home pages",
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = WallpaperScrollMode.STATIC != selectedMode,
                    onClick = { onAction(LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.STATIC)) },
                ) {
                    SettingsButtonText(text = "Static")
                }
                TextButton(
                    enabled = WallpaperScrollMode.SCROLLING != selectedMode,
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

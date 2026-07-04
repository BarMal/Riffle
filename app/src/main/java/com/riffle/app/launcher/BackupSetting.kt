package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupSetting(onAction: (LauncherShellAction) -> Unit) {
    SettingsListRow(
        title = "Launcher backup",
        subtitle = "Layouts, settings, and hidden apps",
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAction(LauncherShellAction.RequestImportLauncherBackup) }) {
                    SettingsButtonText(text = "Import")
                }
                TextButton(onClick = { onAction(LauncherShellAction.ExportLauncherBackup) }) {
                    SettingsButtonText(text = "Export")
                }
            }
        },
    )
}

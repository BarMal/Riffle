package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppRefreshSetting(onAction: (LauncherShellAction) -> Unit) {
    SettingsClickableRow(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_REFRESH_APPS),
        title = SettingKey.LIBRARY_REFRESH_APPS.label(),
        subtitle = "Refetch launchable apps",
        onClick = { onAction(LauncherShellAction.RefreshInstalledApps) },
        trailingContent = {
            SettingsButtonText(text = "Refresh")
        },
    )
}

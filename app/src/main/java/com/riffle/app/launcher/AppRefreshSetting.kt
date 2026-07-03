package com.riffle.app.launcher

import androidx.compose.runtime.Composable

@Composable
fun AppRefreshSetting(onAction: (LauncherShellAction) -> Unit) {
    SettingsClickableRow(
        title = "Installed apps",
        subtitle = "Refetch launchable apps",
        onClick = { onAction(LauncherShellAction.RefreshInstalledApps) },
        trailingContent = {
            SettingsButtonText(text = "Refresh")
        },
    )
}

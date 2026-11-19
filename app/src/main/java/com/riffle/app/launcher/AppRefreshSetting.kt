package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R

@Composable
fun AppRefreshSetting(onAction: (LauncherShellAction) -> Unit) {
    SettingsClickableRow(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_REFRESH_APPS),
        title = SettingKey.LIBRARY_REFRESH_APPS.label(),
        subtitle = stringResource(R.string.settings_refresh_apps_subtitle),
        onClick = { onAction(LauncherShellAction.RefreshInstalledApps) },
        trailingContent = {
            SettingsButtonText(text = stringResource(R.string.action_refresh))
        },
    )
}

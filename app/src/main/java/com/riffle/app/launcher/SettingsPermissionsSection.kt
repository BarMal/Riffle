package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.HomeRoleStatus

@Composable
internal fun SettingsPermissionsSection(
    homeRoleStatus: HomeRoleStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Permissions") {
        SettingsHomeAppSetting(
            status = homeRoleStatus,
            onAction = onAction,
        )
    }
}

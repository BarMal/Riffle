package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus

@Composable
internal fun SettingsPermissionsSection(
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Permissions") {
        SettingsHomeAppSetting(
            status = homeRoleStatus,
            firstRunStatus = firstRunStatus,
            onAction = onAction,
        )
    }
}

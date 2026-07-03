package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.HomeRoleStatus

@Composable
internal fun SettingsLauncherSection(
    status: HomeRoleStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Launcher") {
        HomeAppSetting(
            status = status,
            onAction = onAction,
        )
    }
}

@Composable
private fun HomeAppSetting(
    status: HomeRoleStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsClickableRow(
        title = "Default home app",
        subtitle = status.settingsHomeAppStatusLabel(),
        onClick = { onAction(LauncherShellAction.RequestDefaultHome) },
        trailingContent = {
            SettingsButtonText(
                text =
                    if (status == HomeRoleStatus.DEFAULT_HOME) {
                        "Set"
                    } else {
                        "Set home"
                    },
            )
        },
    )
}

internal fun HomeRoleStatus.settingsHomeAppStatusLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Riffle is default"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Riffle is not default"
        HomeRoleStatus.UNKNOWN -> "Status unknown"
    }

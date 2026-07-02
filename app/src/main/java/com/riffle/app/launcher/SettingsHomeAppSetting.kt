package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Default home app",
            subtitle = status.settingsHomeAppStatusLabel(),
        )
        TextButton(
            enabled = status != HomeRoleStatus.DEFAULT_HOME,
            onClick = { onAction(LauncherShellAction.RequestDefaultHome) },
        ) {
            SettingsButtonText(text = "Set home")
        }
    }
}

internal fun HomeRoleStatus.settingsHomeAppStatusLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Riffle is default"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Riffle is not default"
        HomeRoleStatus.UNKNOWN -> "Status unknown"
    }

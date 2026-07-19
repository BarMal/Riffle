package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.HomeRoleStatus

@Composable
internal fun SettingsLauncherSection(
    status: HomeRoleStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color =
            if (status == HomeRoleStatus.DEFAULT_HOME) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        contentColor =
            if (status == HomeRoleStatus.DEFAULT_HOME) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
    ) {
        SettingsHomeAppSetting(
            status = status,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsHomeAppSetting(
    status: HomeRoleStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsClickableRow(
        title = "Default home app",
        subtitle = status.settingsHomeAppStatusLabel(),
        onClick = { onAction(LauncherShellAction.RequestDefaultHome) },
        trailingContent = {
            SettingsButtonText(text = status.settingsHomeAppActionLabel())
        },
    )
}

internal fun HomeRoleStatus.settingsHomeAppStatusLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Riffle is default"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Riffle is not default"
        HomeRoleStatus.UNKNOWN -> "Home app status unavailable"
    }

internal fun HomeRoleStatus.settingsHomeAppActionLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Default"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Set home"
        HomeRoleStatus.UNKNOWN -> "Try again"
    }

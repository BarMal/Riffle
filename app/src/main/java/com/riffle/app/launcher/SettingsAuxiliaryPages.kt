package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength

@Composable
internal fun SettingsHapticsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Haptics") {
        HapticStrengthSetting(
            selectedStrength = state.settings.haptics.feedbackStrength,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsPermissionsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsPermissionsSection(
        notificationAccessStatus = state.notificationAccessStatus,
        overlayDockPermissionStatus = state.overlayDockPermissionStatus,
        onAction = onAction,
    )
}

@Composable
internal fun SettingsAppsPageContent(onAction: (LauncherShellAction) -> Unit) {
    SettingsSection(title = "Apps") {
        AppRefreshSetting(onAction = onAction)
    }
}

@Composable
internal fun SettingsBackupPageContent(onAction: (LauncherShellAction) -> Unit) {
    SettingsSection(title = "Backup") {
        BackupSetting(onAction = onAction)
    }
}

@Composable
internal fun SettingsHiddenAppsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Hidden apps") {
        HiddenAppsSetting(
            apps = state.hiddenApps,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsVersionPageContent(state: SettingsSurfaceState) {
    SettingsSection(title = "Version") {
        VersionInformationSetting(
            appVersionLabel = state.appVersionLabel,
            appBuildIdentityLabel = state.appBuildIdentityLabel,
        )
    }
}

@Composable
private fun VersionInformationSetting(
    appVersionLabel: String,
    appBuildIdentityLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(
            title = "Version",
            subtitle = appVersionLabel,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsPrimaryText(text = "Build")
            Text(
                text = appBuildIdentityLabel,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HapticStrengthSetting(
    selectedStrength: HapticFeedbackStrength,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Feedback strength",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HapticFeedbackStrength.entries.forEach { strength ->
                TextButton(
                    enabled = strength != selectedStrength,
                    onClick = { onAction(LauncherShellAction.SelectHapticFeedbackStrength(strength)) },
                ) {
                    SettingsButtonText(text = strength.label)
                }
            }
        }
    }
}

@Composable
private fun HiddenAppsSetting(
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (apps.isEmpty()) {
        Text(
            text = "No hidden apps",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                HiddenAppRow(
                    app = app,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: InstalledApp,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = app.label,
            subtitle = app.drawerSubtitle(),
        )
        TextButton(onClick = { onAction(LauncherShellAction.UnhideApp(app.identity)) }) {
            SettingsButtonText(text = "Unhide")
        }
    }
}

private val HapticFeedbackStrength.label: String
    get() =
        when (this) {
            HapticFeedbackStrength.OFF -> "Off"
            HapticFeedbackStrength.LIGHT -> "Light"
            HapticFeedbackStrength.MEDIUM -> "Medium"
            HapticFeedbackStrength.STRONG -> "Strong"
        }

package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R

/**
 * Export and Import. A backup holds your layouts, settings, hidden apps, calendar feed links and which Stream sections
 * are on and how they show; never content (notifications, events, articles) and never any account token.
 */
@Composable
internal fun BackupSetting(onAction: (LauncherShellAction) -> Unit) {
    SettingsClickableRow(
        modifier = Modifier.settingAnchor(SettingKey.BACKUP_EXPORT),
        title = SettingKey.BACKUP_EXPORT.label(),
        subtitle = stringResource(R.string.settings_backup_export_subtitle),
        onClick = { onAction(LauncherShellAction.ExportLauncherBackup) },
        trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_backup_export_action)) },
    )
    SettingsClickableRow(
        modifier = Modifier.settingAnchor(SettingKey.BACKUP_IMPORT),
        title = SettingKey.BACKUP_IMPORT.label(),
        subtitle = stringResource(R.string.settings_backup_import_subtitle),
        onClick = { onAction(LauncherShellAction.RequestImportLauncherBackup) },
        trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_backup_import_action)) },
    )
    SettingsListRow(
        title = stringResource(R.string.settings_backup_never_title),
        subtitle = stringResource(R.string.settings_backup_never_subtitle),
    )
}

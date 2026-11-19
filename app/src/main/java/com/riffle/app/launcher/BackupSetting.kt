package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import java.text.DateFormat
import java.util.Date

/**
 * Export and Import. A backup holds your layouts, settings, hidden apps, calendar feed links and which Stream sections
 * are on and how they show; never content (notifications, events, articles) and never any account token.
 */
@Composable
internal fun BackupSetting(onAction: (LauncherShellAction) -> Unit) {
    val keptAt by (LocalBeforeImportCopies.current?.keptAtEpochMillis ?: NoCopy).collectAsState()
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
    keptAt?.let { at ->
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.BACKUP_UNDO_IMPORT),
            title = stringResource(R.string.settings_backup_undo_import),
            subtitle = stringResource(R.string.settings_backup_undo_import_subtitle, formatImportDate(at)),
            onClick = { onAction(LauncherShellAction.UndoLastBackupImport) },
            trailingContent = {
                SettingsButtonText(
                    text = stringResource(R.string.settings_backup_undo_import_action),
                )
            },
        )
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.BACKUP_DISMISS_UNDO),
            title = stringResource(R.string.settings_backup_dismiss_undo),
            subtitle = stringResource(R.string.settings_backup_dismiss_undo_subtitle),
            onClick = { onAction(LauncherShellAction.DismissLastBackupImportUndo) },
            trailingContent = {
                SettingsButtonText(
                    text = stringResource(R.string.settings_backup_dismiss_undo_action),
                )
            },
        )
    }
    SettingsListRow(
        title = stringResource(R.string.settings_backup_never_title),
        subtitle = stringResource(R.string.settings_backup_never_subtitle),
    )
}

/** The copy kept before the last import, for the Backup page; null where the shell keeps none (previews). */
internal val LocalBeforeImportCopies = staticCompositionLocalOf<BeforeImportCopies?> { null }

private val NoCopy = MutableStateFlow<Long?>(null)

private fun formatImportDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/**
 * Shows "Backup imported" with Undo once, after an import went through (the import itself runs in the activity). The
 * Undo window is the snackbar: whatever ends it closes the hold on Home's widget ids exactly once.
 */
@Composable
internal fun BackupImportAnnouncer(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val copies = LocalBeforeImportCopies.current ?: return
    val message = stringResource(R.string.backup_imported_snackbar)
    val undo = stringResource(R.string.stream_undo)
    // Collected, not keyed on the flag: consuming it must not restart (and so cancel) the snackbar being shown.
    androidx.compose.runtime.LaunchedEffect(copies) {
        copies.announcementPending.filter { it }.collect {
            copies.consumeAnnouncement()
            try {
                val outcome =
                    snackbar.showSnackbar(
                        message = message,
                        actionLabel = undo,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                if (outcome == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    onAction(LauncherShellAction.UndoLastBackupImport)
                }
            } finally {
                copies.closeUndoWindow()
            }
        }
    }
}

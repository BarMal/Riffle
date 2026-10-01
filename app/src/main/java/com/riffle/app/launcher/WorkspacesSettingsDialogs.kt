package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import com.riffle.core.domain.launcher.workspace.settings.CopySource
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsModel

/**
 * Whichever dialog the Workspaces page has open. Each confirms before anything is changed and closes itself;
 * destructive ones then offer Undo through the page's announcement.
 */
@Composable
internal fun WorkspacesDialogs(
    dialog: WorkspaceDialog,
    model: WorkspacesSettingsModel?,
    callbacks: WorkspacesPageCallbacks,
    onDismiss: () -> Unit,
) {
    val apply = { action: WorkspacesSettingsAction ->
        callbacks.onAction(action)
        onDismiss()
    }
    when (dialog) {
        is WorkspaceDialog.Rename ->
            RenameDialog(dialog.name, onDismiss) { name -> apply(WorkspacesSettingsAction.Rename(dialog.id, name)) }
        is WorkspaceDialog.ConfirmDelete ->
            ConfirmDialog(
                title = "Delete workspace?",
                body = WorkspacesDialogText.deleteBody(dialog.name),
                confirm = WorkspacesDialogText.DELETE_CONFIRM,
                onDismiss = onDismiss,
            ) { apply(WorkspacesSettingsAction.Delete(dialog.id)) }
        is WorkspaceDialog.ConfirmReset ->
            ConfirmDialog(
                title = "Reset to preset?",
                body = WorkspacesDialogText.resetBody(dialog.name, dialog.presetName),
                confirm = WorkspacesDialogText.RESET_CONFIRM,
                onDismiss = onDismiss,
            ) { apply(WorkspacesSettingsAction.ResetToPreset(dialog.id)) }
        WorkspaceDialog.PickPreset ->
            PresetPickerDialog(onDismiss) { presetId, activate ->
                apply(WorkspacesSettingsAction.InstallPreset(presetId, activate))
            }
        WorkspaceDialog.PickCopySource ->
            model?.let {
                CopySourceDialog(it, onDismiss) { source -> apply(WorkspacesSettingsAction.CopyFromLayout(source)) }
            }
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename workspace") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(WorkspacesSettingsText.NAME_LABEL) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(WorkspacesSettingsText.SAVE)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

/** The five presets with their descriptions; Nova is marked and pre-selected. Installing never needs a permission. */
@Composable
private fun PresetPickerDialog(
    onDismiss: () -> Unit,
    onInstall: (presetId: String, activate: Boolean) -> Unit,
) {
    var chosen by remember { mutableStateOf(WorkspacePresets.DEFAULT_ID) }
    var activate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WorkspacesDialogText.PRESET_PICKER_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(text = WorkspacesDialogText.PRESET_PICKER_BODY, style = MaterialTheme.typography.bodyMedium)
                WorkspacePresets.all.forEach { preset ->
                    val trailing: (@Composable () -> Unit)? =
                        if (preset.id == WorkspacePresets.DEFAULT_ID) {
                            {
                                Text(
                                    text = WorkspacesSettingsText.DEFAULT_PRESET_TAG,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        } else {
                            null
                        }
                    EditorChoiceRow(
                        title = preset.name,
                        selected = preset.id == chosen,
                        enabled = true,
                        onClick = { chosen = preset.id },
                        supporting = preset.description,
                        trailing = trailing,
                    )
                }
                EditorChoiceRow(
                    title = WorkspacesSettingsText.SWITCH_AFTER_INSTALL,
                    selected = activate,
                    enabled = true,
                    onClick = { activate = !activate },
                    multiple = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInstall(chosen, activate) }) { Text(WorkspacesDialogText.INSTALL_CONFIRM) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

/** Pick the layout to copy from, with the one-time-copy explanation for the chosen one. */
@Composable
private fun CopySourceDialog(
    model: WorkspacesSettingsModel,
    onDismiss: () -> Unit,
    onCopy: (HomeLayoutDeviceClass) -> Unit,
) {
    var chosen by remember { mutableStateOf(model.copySources.firstOrNull()?.layout) }
    val source: CopySource? = model.copySources.firstOrNull { it.layout == chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WorkspacesDialogText.COPY_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                model.copySources.forEach { option ->
                    EditorChoiceRow(
                        title = WorkspacesSettingsText.layoutName(option.layout),
                        selected = option.layout == chosen,
                        enabled = true,
                        onClick = { chosen = option.layout },
                        supporting = WorkspacesDialogText.workspaces(option.workspaceCount),
                    )
                }
                if (source != null) {
                    Text(
                        text =
                            WorkspacesDialogText.copyBody(
                                source = source.layout,
                                target = model.layout,
                                replaced = model.rows.size,
                                copied = source.workspaceCount,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { source?.let { onCopy(it.layout) } }, enabled = source != null) {
                Text(WorkspacesDialogText.COPY_CONFIRM)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

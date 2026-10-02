package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.settings.BrokenUse
import com.riffle.core.domain.launcher.workspace.settings.LensCopyTarget
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction

/** The dialogs the Saved lenses page can have open. Each confirms before anything is changed. */
internal sealed interface LensDialog {
    data class Rename(val id: LensId, val name: String) : LensDialog

    data class ConfirmDelete(val id: LensId, val name: String, val usedBy: Int) : LensDialog

    data class CopyTo(val id: LensId, val name: String, val target: LensCopyTarget) : LensDialog

    /** Leaving the builder with changes that were not saved. */
    data object DiscardChanges : LensDialog

    /**
     * Saving would break containers: choose between detaching them and saving a new lens. [id], [name] and [lens] are
     * the draft; [copyName] is what a new lens would be called.
     */
    data class SaveChoice(
        val id: LensId,
        val name: String,
        val lens: Lens,
        val broken: List<BrokenUse>,
        val copyName: String,
    ) : LensDialog
}

internal const val LENS_DELETE_DETACH_TEST_TAG = "lens-delete-detach"
internal const val LENS_DELETE_REPLACE_TEST_TAG = "lens-delete-replace"
internal const val LENS_SAVE_DETACH_TEST_TAG = "lens-save-detach"
internal const val LENS_SAVE_AS_NEW_TEST_TAG = "lens-save-as-new"
internal const val LENS_RENAME_FIELD_TEST_TAG = "lens-rename-field"

/** Whichever dialog is open; [onDismiss] closes it. Every action closes the dialog it came from. */
@Composable
internal fun LensesDialogs(
    dialog: LensDialog,
    callbacks: LensesPageCallbacks,
    onDismiss: () -> Unit,
) {
    val apply = { action: LensesSettingsAction ->
        callbacks.onDispatch(action)
        onDismiss()
    }
    when (dialog) {
        is LensDialog.Rename ->
            RenameLensDialog(
                dialog,
                callbacks,
                onDismiss,
            ) { name -> apply(LensesSettingsAction.Rename(dialog.id, name)) }
        is LensDialog.ConfirmDelete -> DeleteLensDialog(dialog, callbacks, onDismiss, apply)
        is LensDialog.CopyTo ->
            ConfirmLensDialog(
                title = LensesSettingsText.COPY_TITLE,
                body = LensesDialogText.copyBody(dialog.name, dialog.target),
                confirm = LensesSettingsText.COPY_CONFIRM,
                confirmEnabled = !dialog.target.full,
                onDismiss = onDismiss,
            ) { apply(LensesSettingsAction.CopyToLayout(dialog.id, dialog.target.layout)) }
        LensDialog.DiscardChanges ->
            ConfirmLensDialog(
                title = LensesSettingsText.UNSAVED_TITLE,
                body = LensesSettingsText.UNSAVED_BODY,
                confirm = LensesSettingsText.DISCARD,
                dismissLabel = LensesSettingsText.KEEP_EDITING,
                onDismiss = onDismiss,
            ) {
                callbacks.onCloseDetail()
                onDismiss()
            }
        is LensDialog.SaveChoice -> SaveChoiceDialog(dialog, onDismiss, apply)
    }
}

@Composable
private fun ConfirmLensDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    dismissLabel: String = LensesSettingsText.CANCEL,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
private fun RenameLensDialog(
    dialog: LensDialog.Rename,
    callbacks: LensesPageCallbacks,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(dialog.name) }
    val problem = callbacks.queries.nameProblem(dialog.id, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LensesSettingsText.RENAME) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag(LENS_RENAME_FIELD_TEST_TAG),
                    label = { Text(LensesSettingsText.NAME_LABEL) },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = { Text(LensesMessageText.nameCounter(name.trim().length)) },
                )
                problem?.let {
                    Text(
                        text = LensesMessageText.problem(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = problem == null) { Text(LensesSettingsText.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LensesSettingsText.CANCEL) } },
    )
}

/**
 * Deleting is never blocked: with users, choose between keeping each container's lens as it is (detach) and moving
 * them to another saved lens, with a dry run of how many it cannot serve. Undo is offered after.
 */
@Composable
private fun DeleteLensDialog(
    dialog: LensDialog.ConfirmDelete,
    callbacks: LensesPageCallbacks,
    onDismiss: () -> Unit,
    apply: (LensesSettingsAction) -> Unit,
) {
    val replacements = remember(dialog.id) { callbacks.queries.replacements(dialog.id) }
    var replacing by remember { mutableStateOf(false) }
    var replacement by remember { mutableStateOf(replacements.firstOrNull()?.id) }
    val picked = replacement.takeIf { replacing }
    val policy: RemovePolicy = picked?.let { RemovePolicy.ReplaceWith(it) } ?: RemovePolicy.Detach
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LensesSettingsText.DELETE_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(LensesDialogText.deleteBody(dialog.name, dialog.usedBy))
                if (dialog.usedBy > 0) {
                    EditorChoiceRow(
                        title = LensesSettingsText.DELETE_DETACH,
                        supporting = LensesSettingsText.DELETE_DETACH_HINT,
                        selected = !replacing,
                        enabled = true,
                        onClick = { replacing = false },
                        modifier = Modifier.testTag(LENS_DELETE_DETACH_TEST_TAG),
                    )
                    EditorChoiceRow(
                        title = LensesSettingsText.DELETE_REPLACE,
                        supporting = LensesSettingsText.DELETE_REPLACE_HINT,
                        selected = replacing,
                        enabled = replacements.isNotEmpty(),
                        reason = LensesSettingsText.NO_REPLACEMENT.takeIf { replacements.isEmpty() },
                        onClick = { replacing = true },
                        modifier = Modifier.testTag(LENS_DELETE_REPLACE_TEST_TAG),
                    )
                    if (replacing) {
                        replacements.forEach { option ->
                            EditorChoiceRow(
                                title = option.name,
                                selected = option.id == replacement,
                                enabled = true,
                                onClick = { replacement = option.id },
                            )
                        }
                        picked?.let { id ->
                            Text(
                                text = LensesDialogText.replacementImpact(callbacks.queries.replacementImpact(dialog.id, id)),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { apply(LensesSettingsAction.Delete(dialog.id, policy)) }) {
                Text(LensesSettingsText.DELETE_CONFIRM)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LensesSettingsText.CANCEL) } },
    )
}

/** Saving would stop some containers working (design 4.4): list them with why, and offer the two honest ways out. */
@Composable
private fun SaveChoiceDialog(
    dialog: LensDialog.SaveChoice,
    onDismiss: () -> Unit,
    apply: (LensesSettingsAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LensesSettingsText.BREAKS_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(LensesDetailText.breaksSummary(dialog.broken.size))
                dialog.broken.forEach { use ->
                    Text(text = LensesDetailText.broken(use), style = MaterialTheme.typography.bodySmall)
                }
                Text(LensesSettingsText.SAVE_DETACH_HINT, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { apply(LensesSettingsAction.Create(dialog.copyName, dialog.lens)) },
                    modifier = Modifier.fillMaxWidth().testTag(LENS_SAVE_AS_NEW_TEST_TAG),
                ) { Text(LensesDialogText.saveAsNewLabel(dialog.copyName)) }
                Text(LensesSettingsText.SAVE_AS_NEW_HINT, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    apply(
                        LensesSettingsAction.Save(dialog.id, dialog.name, dialog.lens, BreakPolicy.DETACH_BROKEN),
                    )
                },
                modifier = Modifier.testTag(LENS_SAVE_DETACH_TEST_TAG),
            ) { Text(LensesSettingsText.SAVE_DETACH) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LensesSettingsText.CANCEL) } },
    )
}

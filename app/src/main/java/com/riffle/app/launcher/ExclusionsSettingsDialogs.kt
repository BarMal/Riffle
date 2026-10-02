package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.app.launcher.exclusions.ExclusionsAnnouncements
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleDescriber
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleRow
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.SourceCatalog
import com.riffle.core.domain.launcher.workspace.settings.TextRuleDraft
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem
import com.riffle.core.domain.launcher.workspace.settings.TextRuleValidator

internal const val EXCLUSION_VALUE_FIELD_TEST_TAG = "exclusion-add-value"
internal const val EXCLUSION_PROBLEM_TEST_TAG = "exclusion-add-problem"

/** Whichever dialog the Hidden items page has open. Each closes itself; deleting offers Undo afterwards. */
@Composable
internal fun ExclusionsDialogs(
    dialog: ExclusionDialog,
    rowFor: (ExclusionRuleId) -> ExclusionRuleRow?,
    callbacks: ExclusionsPageCallbacks,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is ExclusionDialog.ConfirmDelete ->
            rowFor(dialog.id)?.let { row ->
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text(ExclusionsSettingsText.DELETE_TITLE) },
                    text = { Text(ExclusionsSettingsText.deleteBody(row)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                callbacks.onAction(ExclusionsSettingsAction.Delete(dialog.id))
                                onDismiss()
                            },
                        ) { Text(ExclusionsSettingsText.DELETE_CONFIRM) }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text(ExclusionsSettingsText.CANCEL) } },
                )
            }
        ExclusionDialog.AddText ->
            AddTextRuleDialog(
                problemWith = callbacks.problemWith,
                onAdd = { draft ->
                    callbacks.onAction(ExclusionsSettingsAction.AddText(draft))
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
    }
}

/**
 * Adds a text rule: where it applies, which field, how it matches and the text. The reason a rule cannot be added
 * is shown live (a polite live region) and the button stays disabled until there is none; nothing is created from
 * anything but what is typed here.
 */
@Composable
internal fun AddTextRuleDialog(
    problemWith: (TextRuleDraft) -> TextRuleProblem?,
    onAdd: (TextRuleDraft) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
) {
    var source by remember { mutableStateOf(TextRuleValidator.SOURCES.first()) }
    var field by remember { mutableStateOf(ExclusionTextField.TITLE) }
    var mode by remember { mutableStateOf(ExclusionMatchMode.CONTAINS) }
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val draft = TextRuleDraft(source, field, mode, value)
    val problem = problemWith(draft)
    // Nothing typed yet is not an error to show, only a button that is not ready.
    val shownProblem = problem.takeIf { value.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ExclusionsSettingsText.ADD_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                ChoiceGroup(
                    label = ExclusionsSettingsText.SOURCE_LABEL,
                    options = TextRuleValidator.SOURCES,
                    selected = source,
                    title = { SourceCatalog.infoFor(it).title },
                    onSelect = { source = it },
                )
                ChoiceGroup(
                    label = ExclusionsSettingsText.FIELD_LABEL,
                    options = ExclusionTextField.entries,
                    selected = field,
                    title = ExclusionRuleDescriber::fieldName,
                    onSelect = { field = it },
                )
                ChoiceGroup(
                    label = ExclusionsSettingsText.MODE_LABEL,
                    options = ExclusionMatchMode.entries,
                    selected = mode,
                    title = ExclusionsSettingsText::modeName,
                    onSelect = { mode = it },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag(EXCLUSION_VALUE_FIELD_TEST_TAG),
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(ExclusionsSettingsText.VALUE_LABEL) },
                    singleLine = true,
                    isError = shownProblem != null,
                )
                Text(
                    text =
                        ExclusionsSettingsText.ADD_HELP +
                            if (mode == ExclusionMatchMode.WILDCARD) " ${ExclusionsSettingsText.WILDCARD_HELP}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    modifier =
                        Modifier
                            .testTag(EXCLUSION_PROBLEM_TEST_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    text = shownProblem?.let(ExclusionsAnnouncements::problem).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(draft) }, enabled = problem == null) {
                Text(ExclusionsSettingsText.ADD_CONFIRM)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ExclusionsSettingsText.CANCEL) } },
    )
}

@Composable
private fun <T> ChoiceGroup(
    label: String,
    options: List<T>,
    selected: T,
    title: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        options.forEach { option ->
            EditorChoiceRow(
                title = title(option),
                selected = option == selected,
                enabled = true,
                onClick = { onSelect(option) },
            )
        }
    }
}

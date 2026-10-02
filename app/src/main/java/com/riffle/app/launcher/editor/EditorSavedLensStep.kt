package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.SavedLensChoice
import com.riffle.core.domain.launcher.workspace.editor.SavedLensFlow

internal const val EDITOR_SAVED_LENS_ENTRY_TEST_TAG = "editor-saved-lens-entry"
internal const val EDITOR_SAVED_LENS_BUILD_OWN_TEST_TAG = "editor-saved-lens-build-own"
internal const val EDITOR_SAVED_LENS_IN_USE_TEST_TAG = "editor-saved-lens-in-use"
internal const val EDITOR_SAVED_LENS_DETACH_TEST_TAG = "editor-saved-lens-detach"
internal const val EDITOR_SAVE_AS_TOGGLE_TEST_TAG = "editor-save-as-toggle"
internal const val EDITOR_SAVE_AS_NAME_TEST_TAG = "editor-save-as-name"

internal fun savedLensRowTestTag(id: LensId): String = "editor-saved-lens-row-${id.value}"

/**
 * The saved-lens part of the Source step, in place of the lens builder when [showsSavedLensStep]: the list to choose
 * from ([BindingFlowState.choosingSaved]), or the summary of the saved lens the binding uses ([BindingFlowState.ref])
 * with Detach.
 */
@Composable
internal fun EditorSavedLensStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.choosingSaved -> SavedLensList(state, context, onFlowAction, modifier)
        state.ref != null -> SavedLensInUse(state, context, onFlowAction, modifier)
    }
}

/** Whether the Source step shows [EditorSavedLensStep] instead of the lens builder. */
internal fun showsSavedLensStep(state: BindingFlowState): Boolean = state.choosingSaved || state.ref != null

/** The way into the list: a 48 dp button with the count, or a short hint when the layout has none yet. */
@Composable
internal fun SavedLensEntry(
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = context.scope.library.lenses.size
    if (count == 0) {
        Text(
            EditorLensText.NONE_SAVED,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.testTag(EDITOR_SAVED_LENS_ENTRY_TEST_TAG),
        )
    } else {
        OutlinedButton(
            onClick = { onFlowAction(BindingFlowAction.ShowSavedLenses) },
            modifier = modifier.heightIn(min = RiffleSpacing.xxxl).testTag(EDITOR_SAVED_LENS_ENTRY_TEST_TAG),
        ) { Text(EditorLensText.listLabel(count)) }
    }
}

/**
 * Every saved lens of the layout, by name. A lens the container cannot use stays in the list, disabled, with the
 * domain's reason (never hidden). Each row is one 48 dp target that reads name, sources, shape, use count, the reason
 * and its position as one sentence.
 */
@Composable
private fun SavedLensList(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier,
) {
    val choices = SavedLensFlow.choices(context)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorLensText.SAVED_HEADING)
        choices.forEachIndexed { index, choice ->
            SavedLensRow(choice, state.ref == choice.saved.id, index + 1, choices.size, onFlowAction)
        }
        TextButton(
            onClick = { onFlowAction(BindingFlowAction.ShowBuilder) },
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(EDITOR_SAVED_LENS_BUILD_OWN_TEST_TAG),
        ) { Text(EditorLensText.BUILD_OWN) }
    }
}

@Composable
private fun SavedLensRow(
    choice: SavedLensChoice,
    selected: Boolean,
    position: Int,
    total: Int,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    val reason = choice.block?.let { EditorLensReasons.savedLens(it) }
    EditorChoiceRow(
        title = choice.saved.name,
        selected = selected,
        enabled = choice.enabled,
        onClick = { onFlowAction(BindingFlowAction.UseSavedLens(choice.saved.id)) },
        modifier = Modifier.testTag(savedLensRowTestTag(choice.saved.id)),
        supporting = EditorLensText.summary(choice),
        reason = reason,
        spoken = EditorLensText.spoken(choice, reason, position, total),
    )
}

/** The binding uses a saved lens: its summary, why the builder is closed, and Detach (48 dp). */
@Composable
private fun SavedLensInUse(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier,
) {
    val choice = SavedLensFlow.choices(context).firstOrNull { it.saved.id == state.ref } ?: return
    Surface(
        modifier = modifier.fillMaxWidth().testTag(EDITOR_SAVED_LENS_IN_USE_TEST_TAG),
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = RiffleElevation.level0,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(EditorLensText.usingTitle(choice.saved.name), style = MaterialTheme.typography.titleMedium)
            Text(EditorLensText.summary(choice), style = MaterialTheme.typography.bodyMedium)
            Text(EditorLensText.USING_NOTE, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                Button(
                    onClick = { onFlowAction(BindingFlowAction.DetachSavedLens) },
                    modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(EDITOR_SAVED_LENS_DETACH_TEST_TAG),
                ) { Text(EditorLensText.DETACH) }
                OutlinedButton(
                    onClick = { onFlowAction(BindingFlowAction.ShowSavedLenses) },
                    modifier = Modifier.heightIn(min = RiffleSpacing.xxxl),
                ) { Text(EditorLensText.CHOOSE_ANOTHER) }
            }
        }
    }
}

/**
 * Save as lens on the Confirm step: a checkbox row (disabled with the reason when the layout's library is full), and,
 * once ticked, the name field. The field is controlled by the flow state (the unique suggestion is in it from the
 * start), capped at the library's name length with a counter, and shows the library's reason as it types. Nothing is
 * created until the user confirms the step.
 */
@Composable
internal fun SaveAsLensBlock(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.ref != null || state.draft.toLens() == null) return
    val asked = state.saveAs
    val available = SavedLensFlow.canSaveAs(state, context)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorChoiceRow(
            title = EditorLensText.SAVE_AS,
            selected = asked != null,
            enabled = available || asked != null,
            onClick = {
                val lens = checkNotNull(state.draft.toLens())
                onFlowAction(
                    if (asked == null) {
                        BindingFlowAction.StartSaveAsLens(EditorLensNames.suggest(lens, state.draft.preset))
                    } else {
                        BindingFlowAction.CancelSaveAsLens
                    },
                )
            },
            modifier = Modifier.testTag(EDITOR_SAVE_AS_TOGGLE_TEST_TAG),
            supporting = EditorLensText.SAVE_AS_HELP,
            reason = EditorLensText.SAVE_AS_FULL.takeUnless { available || asked != null },
            multiple = true,
        )
        if (asked != null) SaveAsNameField(asked.name, SavedLensFlow.saveAsProblem(state, context), onFlowAction)
    }
}

@Composable
private fun SaveAsNameField(
    name: String,
    problem: LibraryProblem?,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onFlowAction(BindingFlowAction.SetSaveAsName(it.take(MAX_SAVED_LENS_NAME))) },
        modifier = Modifier.fillMaxWidth().testTag(EDITOR_SAVE_AS_NAME_TEST_TAG),
        singleLine = true,
        isError = problem != null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        label = { Text(EditorLensText.NAME_LABEL) },
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = problem?.let { EditorLensMessages.libraryProblem(it) }.orEmpty(),
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(EditorLensText.nameCounter(name.trim().length))
            }
        },
    )
}

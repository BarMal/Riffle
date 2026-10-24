package com.riffle.app.launcher.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.app.launcher.editor.EditorReasonText
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoal
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.DestinationChoice
import com.riffle.core.domain.launcher.workspace.wizard.GoalAvailability
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow
import com.riffle.core.domain.launcher.workspace.wizard.WizardProblem
import com.riffle.core.domain.launcher.workspace.wizard.WizardState

/**
 * Source: every goal of the catalog. One that cannot be chosen here stays visible, disabled, with its reason; one
 * that needs access is choosable with a "Needs access" badge and, once chosen, the explanation and Review access.
 */
@Composable
internal fun WizardSourceStep(
    state: WizardState,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Text(
            WizardText.SOURCE_SUPPORTING,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WizardFlow.goalChoices(context).forEach { choice ->
            val goal = choice.goal
            val label = WizardText.availability(choice.availability)
            val selected = state.setup?.goal == goal.id
            val badge = label.takeIf { choice.availability == GoalAvailability.NEEDS_ACCESS }
            val reason = label.takeIf { !choice.enabled }
            EditorChoiceRow(
                title = goal.label,
                selected = selected,
                enabled = choice.enabled,
                onClick = { onAction(WizardAction.ChooseGoal(goal.id)) },
                supporting = listOfNotNull(goal.description, badge).joinToString(" - "),
                reason = reason,
                modifier = Modifier.testTag(wizardGoalTag(goal.id.key)),
                spoken = WizardText.spoken(goal.label, goal.description, badge, reason),
            )
            if (selected && choice.needs.isNotEmpty()) {
                NeedsAccessNote(goal, choice.needs, onRequestSourceAccess)
            }
        }
    }
}

/**
 * Why a chosen goal shows "Needs access", and the existing explicit flow per permission (rationale first; nothing
 * is requested unless the user taps Review access).
 */
@Composable
internal fun NeedsAccessNote(
    goal: ContentGoal,
    needs: List<SourcePermission>,
    onRequestSourceAccess: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RiffleShapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = RiffleSpacing.m, end = RiffleSpacing.xs, top = RiffleSpacing.s),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(WizardText.NEEDS_ACCESS, style = MaterialTheme.typography.labelLarge)
            needs.forEach { permission ->
                Text(WizardText.permission(permission), style = MaterialTheme.typography.bodyMedium)
                val source = goal.sources.firstOrNull { SourcePermission.forSource(it) == permission }
                if (source != null) {
                    TextButton(
                        onClick = { onRequestSourceAccess(source) },
                        modifier =
                            Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(wizardReviewAccessTag(permission)),
                    ) { Text(WizardText.REVIEW_ACCESS) }
                }
            }
            Text(
                WizardText.NEEDS_ACCESS_NOTE,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = RiffleSpacing.s),
            )
        }
    }
}

/**
 * Place: each destination the chosen look suits, enabled exactly when the editor would accept it (a refused one says
 * why), then "Save as a lens only". A widget lists each place it could go when there is more than one.
 */
@Composable
internal fun WizardPlaceStep(
    state: WizardState,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
) {
    val goal = state.setup?.goal
    val workspace = context.workspace
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        WizardFlow.destinationChoices(state, context).forEach { choice ->
            if (choice.destination == Destination.WIDGET && choice.enabled && choice.widgetTargets.size > 1) {
                choice.widgetTargets.forEachIndexed { index, target ->
                    val label = WizardText.widget(target, workspace)
                    EditorChoiceRow(
                        title = label,
                        selected = state.destination == Destination.WIDGET && state.widgetTarget == target,
                        enabled = true,
                        onClick = { onAction(WizardAction.ChooseDestination(Destination.WIDGET, target)) },
                        modifier = Modifier.testTag(wizardDestinationTag(Destination.WIDGET, index)),
                    )
                }
            } else {
                DestinationRow(state, choice, goal, context, onAction)
            }
        }
    }
}

@Composable
private fun DestinationRow(
    state: WizardState,
    choice: DestinationChoice,
    goal: ContentGoalId?,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
) {
    val label = WizardText.destination(choice.destination, goal, choice.widgetTargets.firstOrNull(), context.workspace)
    val supporting = WizardText.destinationSupporting(choice.destination, choice.replaces)
    val reason =
        if (choice.enabled) null else choice.rejection?.let(EditorReasonText::rejection) ?: WizardText.DOES_NOT_FIT
    EditorChoiceRow(
        title = label,
        selected = state.destination == choice.destination,
        enabled = choice.enabled,
        onClick = { onAction(WizardAction.ChooseDestination(choice.destination)) },
        supporting = supporting,
        reason = reason,
        modifier = Modifier.testTag(wizardDestinationTag(choice.destination)),
        spoken = WizardText.spoken(label, supporting, reason),
    )
}

/**
 * Save: "Save to your lenses" (on by default from the editor and Settings; required for a lens only) and its name,
 * the suggestion until the user types one, with a 40-character counter. A name problem shows on the field.
 */
@Composable
internal fun WizardSaveStep(
    state: WizardState,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
) {
    val required = state.destination == Destination.LENS_ONLY
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        if (!required) {
            SaveSwitch(state.saveToLibrary) { onAction(WizardAction.SetSaveToLibrary(it)) }
        }
        if (WizardFlow.saves(state)) {
            NameField(state, context, onAction)
        }
    }
}

@Composable
private fun SaveSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RiffleSpacing.xxxl)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag(WIZARD_SAVE_SWITCH_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(WizardText.SAVE_SWITCH, style = MaterialTheme.typography.bodyLarge)
            Text(
                WizardText.SAVE_SUPPORTING,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NameField(
    state: WizardState,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
) {
    var text by rememberSaveable(state.setup?.goal) { mutableStateOf(WizardFlow.name(state, context)) }
    val problem =
        WizardFlow.problems(state, context).filterIsInstance<WizardProblem.Name>().firstOrNull()
            ?.let(WizardText::problem)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onAction(WizardAction.SetName(it))
        },
        label = { Text(WizardText.NAME) },
        supportingText = {
            Text(listOfNotNull(problem, "${text.trim().length}/$MAX_SAVED_LENS_NAME").joinToString(" "))
        },
        isError = problem != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_NAME_TAG),
    )
}

/** The Content step: the shared per-goal content options wired to the wizard's actions. */
@Composable
internal fun WizardContentStep(
    state: WizardState,
    context: WizardContext,
    apps: List<AppPick>,
    onAction: (WizardAction) -> Unit,
) {
    val setup = state.setup ?: return
    GoalContentOptions(setup, apps, context.feeds, wizardCallbacks(onAction))
}

/** The Look step: every look of the goal; one that cannot go anywhere from this entry is disabled with its reason. */
@Composable
internal fun WizardLookStep(
    state: WizardState,
    context: WizardContext,
    onAction: (WizardAction) -> Unit,
) {
    val setup = state.setup ?: return
    val looks = WizardFlow.lookChoices(state, context).map { LookOption(it.look, it.enabled) }
    GoalLookOptions(setup, looks, onLook = { onAction(WizardAction.ChooseLook(it)) })
}

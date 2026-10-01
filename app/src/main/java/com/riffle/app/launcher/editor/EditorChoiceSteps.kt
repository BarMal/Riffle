package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.ContainerChoice
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget

/**
 * Step 2: how the lens is drawn. Every expression is listed; only the ones the domain says can draw this lens
 * can be selected, and the rest say why not.
 */
@Composable
internal fun EditorExpressionStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorText.EXPRESSION_HEADING)
        BindingFlow.expressionChoices(state, context).forEach { choice ->
            val hint = EditorText.expressionHint(choice.kind)
            EditorChoiceRow(
                title = EditorText.expressionLabel(choice.kind),
                selected = state.expression == choice.kind,
                enabled = choice.enabled,
                onClick = { onFlowAction(BindingFlowAction.PickExpression(choice.kind)) },
                supporting = if (choice.perGroupOnly) "$hint. ${EditorText.PER_GROUP_NOTE}" else hint,
                reason = EditorReasonText.expression(choice),
            )
        }
    }
}

/**
 * Step 3: where it goes. Only offered when adding; a choice is enabled exactly when the editor would accept it.
 * A widget also picks its place: a free spot on an existing widget page, or a new one.
 */
@Composable
internal fun EditorContainerStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorText.CONTAINER_HEADING)
        BindingFlow.containerChoices(state, context).forEach { choice ->
            EditorChoiceRow(
                title = EditorText.containerLabel(choice.kind),
                selected = state.container == choice.kind,
                enabled = choice.enabled,
                onClick = { onFlowAction(BindingFlowAction.PickContainer(choice.kind)) },
                supporting = EditorText.containerHint(choice.kind),
                reason = choice.rejection?.let { EditorReasonText.rejection(it) },
            )
            if (state.container == choice.kind && choice.widgetTargets.size > 1) {
                WidgetTargetRows(choice, state.widgetTarget, context, onFlowAction)
            }
        }
    }
}

@Composable
private fun WidgetTargetRows(
    choice: ContainerChoice,
    selected: WidgetTarget?,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = RiffleSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) {
        choice.widgetTargets.forEach { target ->
            EditorChoiceRow(
                title = widgetTargetLabel(target, context),
                selected = target == selected,
                enabled = true,
                onClick = { onFlowAction(BindingFlowAction.PickContainer(choice.kind, target)) },
            )
        }
    }
}

internal fun widgetTargetLabel(
    target: WidgetTarget,
    context: BindingFlowContext,
): String {
    val index = context.workspace.pages.indexOfFirst { it.id == target.pageId }
    return if (target.pageId == null || index < 0) "On a new widget page" else "On page ${index + 1}"
}

/** Step 4: a plain summary of what will be added. Nothing changes until the user confirms. */
@Composable
internal fun EditorConfirmStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorText.CONFIRM_HEADING)
        confirmSummary(state, context).forEach { (label, value) ->
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Label/value lines for the Confirm step. */
internal fun confirmSummary(
    state: BindingFlowState,
    context: BindingFlowContext,
): List<Pair<String, String>> =
    buildList {
        add("From" to state.draft.sources.joinToString(", ") { EditorText.sourceLabel(it) })
        add("Showing" to (state.draft.preset?.let { EditorText.presetLabel(it) } ?: EditorText.CUSTOM_LENS))
        state.expression?.let { add("Look" to EditorText.expressionLabel(it)) }
        state.container?.let { kind ->
            val where =
                state.widgetTarget?.takeIf { kind == ContainerKind.WIDGET }
                    ?.let { widgetTargetLabel(it, context) }
            add("Placed as" to listOfNotNull(EditorText.containerLabel(kind), where).joinToString(", "))
        }
    }

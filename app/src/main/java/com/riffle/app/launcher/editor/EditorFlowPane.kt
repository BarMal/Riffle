package com.riffle.app.launcher.editor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode

/** The steps a flow shows: re-binding an existing container skips the Container step. */
internal fun flowSteps(mode: FlowMode): List<EditorStep> =
    if (mode == FlowMode.Add) {
        EditorStep.entries
    } else {
        listOf(EditorStep.SOURCE, EditorStep.EXPRESSION, EditorStep.CONFIRM)
    }

/** Why Next is unavailable, in words; null when it is available. */
internal fun nextHint(
    state: BindingFlowState,
    context: BindingFlowContext,
): String? =
    when {
        BindingFlow.canAdvance(state, context) -> null
        state.step == EditorStep.SOURCE && state.draft.sources.isEmpty() -> "Choose at least one source."
        state.step == EditorStep.SOURCE -> "Nothing can draw this yet. Try another preset or source."
        state.step == EditorStep.EXPRESSION -> "Choose how it should look."
        state.step == EditorStep.CONTAINER -> "Choose where it should go."
        else -> null
    }

/**
 * The flow's body: a step header, the step, and Back / Next buttons. Every action is a button, so nothing
 * depends on a gesture. The step change cross-fades, or snaps under reduced motion.
 */
@Composable
internal fun EditorFlowPane(
    flow: ActiveFlow,
    context: BindingFlowContext,
    onAction: (EditorAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = flow.state
    val onFlowAction: (BindingFlowAction) -> Unit = { onAction(EditorAction.Flow(it)) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
        FlowHeader(flow, onCancel = { onAction(EditorAction.CancelFlow) })
        Crossfade(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            animationSpec = RiffleMotion.standard(reducedMotion),
            label = "editor-step",
        ) { step ->
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    EditorStep.SOURCE ->
                        EditorSourceStep(state, context, onFlowAction, onRequestSourceAccess)
                    EditorStep.EXPRESSION -> EditorExpressionStep(state, context, onFlowAction)
                    EditorStep.CONTAINER -> EditorContainerStep(state, context, onFlowAction)
                    EditorStep.CONFIRM -> EditorConfirmStep(state, context)
                }
            }
        }
        nextHint(state, context)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowButtons(flow, context, onAction)
    }
}

@Composable
private fun FlowHeader(
    flow: ActiveFlow,
    onCancel: () -> Unit,
) {
    val steps = flowSteps(flow.mode)
    val position = steps.indexOf(flow.state.step) + 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = EditorText.stepProgress(position, steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = EditorText.stepTitle(flow.state.step), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = onCancel) { Text(EditorText.CANCEL) }
    }
}

@Composable
private fun FlowButtons(
    flow: ActiveFlow,
    context: BindingFlowContext,
    onAction: (EditorAction) -> Unit,
) {
    val state = flow.state
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = RiffleSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m, Alignment.End),
    ) {
        if (state.step != EditorStep.SOURCE) {
            OutlinedButton(onClick = { onAction(EditorAction.Flow(BindingFlowAction.Back)) }) { Text(EditorText.BACK) }
        }
        if (state.step == EditorStep.CONFIRM) {
            val label = if (flow.mode == FlowMode.Add) EditorText.ADD_TO_WORKSPACE else EditorText.APPLY_CHANGES
            Button(onClick = { onAction(EditorAction.ConfirmFlow) }) { Text(label) }
        } else {
            Button(
                onClick = { onAction(EditorAction.Flow(BindingFlowAction.Next)) },
                enabled = BindingFlow.canAdvance(state, context),
            ) { Text(EditorText.NEXT) }
        }
    }
}

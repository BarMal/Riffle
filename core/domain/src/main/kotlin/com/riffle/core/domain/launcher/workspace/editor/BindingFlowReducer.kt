package com.riffle.core.domain.launcher.workspace.editor

/**
 * Write side of the editor flow. Pure: `(state, action, context) -> state`. An action that is not allowed
 * right now (wrong step, disabled option, unselectable source) returns the state unchanged, so the UI
 * cannot reach an invalid selection even if it forwards a stale tap.
 */
object BindingFlowReducer {
    fun reduce(
        state: BindingFlowState,
        action: BindingFlowAction,
        context: BindingFlowContext,
    ): BindingFlowState =
        when (action) {
            is BindingFlowAction.ToggleSource -> onSource(state, action, context)
            is BindingFlowAction.ApplyPreset -> onPreset(state, action, context)
            is BindingFlowAction.SetFilter -> onLens(state, context) { it.withFilter(action.filter) }
            is BindingFlowAction.SetGroup -> onLens(state, context) { it.withGroup(action.group) }
            is BindingFlowAction.SetSort -> onLens(state, context) { it.withSort(action.sort) }
            is BindingFlowAction.SetLimit -> onLens(state, context) { it.withLimit(action.limit) }
            is BindingFlowAction.PickExpression -> onExpression(state, action, context)
            is BindingFlowAction.PickContainer -> onContainer(state, action, context)
            BindingFlowAction.Next -> next(state, context)
            BindingFlowAction.Back -> state.copy(step = previous(state, context))
        }

    private fun onSource(
        state: BindingFlowState,
        action: BindingFlowAction.ToggleSource,
        context: BindingFlowContext,
    ): BindingFlowState {
        val removing = action.id in state.draft.sources
        val allowed = removing || context.sources.any { it.id == action.id && it.selectable }
        return if (allowed) onLens(state, context) { it.toggleSource(action.id) } else state
    }

    private fun onPreset(
        state: BindingFlowState,
        action: BindingFlowAction.ApplyPreset,
        context: BindingFlowContext,
    ): BindingFlowState {
        val enabled = BindingFlow.presetChoices(state, context).firstOrNull { it.preset == action.preset }?.enabled
        return if (enabled == true) onLens(state, context) { it.withPreset(action.preset) } else state
    }

    private fun onLens(
        state: BindingFlowState,
        context: BindingFlowContext,
        change: (LensDraft) -> LensDraft,
    ): BindingFlowState =
        if (state.step == EditorStep.SOURCE) reconcile(state.copy(draft = change(state.draft)), context) else state

    /** Drops any selection the new lens no longer allows. */
    private fun reconcile(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): BindingFlowState {
        val expressionOk =
            state.expression != null &&
                BindingFlow.expressionChoices(state, context).any { it.kind == state.expression && it.enabled }
        val withExpression =
            if (expressionOk) {
                state
            } else {
                state.copy(
                    expression = null,
                    container = null,
                    widgetTarget = null,
                )
            }
        val choice =
            BindingFlow.containerChoices(
                withExpression,
                context,
            ).firstOrNull { it.kind == withExpression.container }
        return if (choice != null && choice.enabled) {
            withExpression.copy(widgetTarget = pickTarget(choice, withExpression.widgetTarget))
        } else {
            withExpression.copy(container = null, widgetTarget = null)
        }
    }

    private fun pickTarget(
        choice: ContainerChoice,
        wanted: WidgetTarget?,
    ): WidgetTarget? =
        if (choice.kind == ContainerKind.WIDGET) {
            wanted.takeIf {
                it in choice.widgetTargets
            } ?: choice.widgetTargets.firstOrNull()
        } else {
            null
        }

    private fun onExpression(
        state: BindingFlowState,
        action: BindingFlowAction.PickExpression,
        context: BindingFlowContext,
    ): BindingFlowState {
        val enabled = BindingFlow.expressionChoices(state, context).firstOrNull { it.kind == action.kind }?.enabled
        return if (state.step == EditorStep.EXPRESSION && enabled == true) {
            state.copy(expression = action.kind, container = null, widgetTarget = null)
        } else {
            state
        }
    }

    private fun onContainer(
        state: BindingFlowState,
        action: BindingFlowAction.PickContainer,
        context: BindingFlowContext,
    ): BindingFlowState {
        val choice = BindingFlow.containerChoices(state, context).firstOrNull { it.kind == action.kind }
        return if (state.step == EditorStep.CONTAINER && choice != null && choice.enabled) {
            state.copy(container = action.kind, widgetTarget = pickTarget(choice, action.target))
        } else {
            state
        }
    }

    private fun next(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): BindingFlowState {
        if (!BindingFlow.canAdvance(state, context)) return state
        val target =
            when (state.step) {
                EditorStep.SOURCE -> EditorStep.EXPRESSION
                EditorStep.EXPRESSION -> if (context.mode == FlowMode.Add) EditorStep.CONTAINER else EditorStep.CONFIRM
                else -> EditorStep.CONFIRM
            }
        return state.copy(step = target)
    }

    private fun previous(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): EditorStep =
        when (state.step) {
            EditorStep.SOURCE, EditorStep.EXPRESSION -> EditorStep.SOURCE
            EditorStep.CONTAINER -> EditorStep.EXPRESSION
            EditorStep.CONFIRM -> if (context.mode == FlowMode.Add) EditorStep.CONTAINER else EditorStep.EXPRESSION
        }
}

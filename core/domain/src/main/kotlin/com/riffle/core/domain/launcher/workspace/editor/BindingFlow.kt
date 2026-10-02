package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding

/**
 * Read side of the Source -> Expression -> Container -> Confirm flow: what each step offers, whether it
 * can advance, and the edit the flow produces. [BindingFlowReducer] is the write side. Every option here is
 * derived from `LensExpressionValidity` and [WorkspaceEditor], so an invalid combination is never offered.
 */
object BindingFlow {
    fun start(context: BindingFlowContext): BindingFlowState {
        val existing = FlowEdits.existingBinding(context) ?: return BindingFlowState()
        val ref = existing.ref?.takeIf { context.scope.library.find(it)?.lens == existing.lens }
        return BindingFlowState(draft = LensDraft.from(existing.lens), expression = existing.expression, ref = ref)
    }

    fun lens(state: BindingFlowState): Lens? = state.draft.toLens()

    fun presetChoices(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): List<PresetChoice> = LensDraft.presetChoices(context.sources.filter { it.id in state.draft.sources })

    fun expressionChoices(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): List<ExpressionChoice> = lens(state)?.let { expressionChoicesFor(it, context) }.orEmpty()

    /** The Expression step's options for [lens] (the draft's, or a saved lens being considered). */
    fun expressionChoicesFor(
        lens: Lens,
        context: BindingFlowContext,
    ): List<ExpressionChoice> {
        val acceptedBy: ((ExpressionKind) -> EditResult)? =
            if (context.mode == FlowMode.Add) null else { kind -> tryEdit(context, LensBinding(lens, kind)) }
        return ExpressionOptions.forLens(lens, context.edit, acceptedBy)
    }

    private fun tryEdit(
        context: BindingFlowContext,
        binding: LensBinding,
    ): EditResult = WorkspaceEditor.apply(context.workspace, FlowEdits.editFor(context, binding), context.edit)

    /** Empty until an expression is picked, and always empty when re-binding (the container is fixed). */
    fun containerChoices(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): List<ContainerChoice> {
        val binding = bindingOf(state)
        return if (binding == null || context.mode != FlowMode.Add) {
            emptyList()
        } else {
            ContainerOptions.forBinding(binding, context.workspace, context.edit)
        }
    }

    fun canAdvance(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): Boolean =
        when (state.step) {
            EditorStep.SOURCE -> expressionChoices(state, context).any { it.enabled }
            EditorStep.EXPRESSION -> state.expression != null
            EditorStep.CONTAINER -> state.container != null
            EditorStep.CONFIRM -> false
        }

    /** The edit to apply on the Confirm step. Re-checked against the editor, so stale state cannot slip through. */
    fun confirm(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): FlowOutcome {
        val (saved, problem) = SavedLensFlow.plan(state, context)
        val ref = saved?.id ?: SavedLensFlow.current(state, context)?.id
        val binding = bindingOf(state)?.copy(ref = ref)
        val edit =
            when {
                problem != null || state.step != EditorStep.CONFIRM || binding == null -> null
                context.mode == FlowMode.Add -> FlowEdits.addEdit(state, binding, context)
                else -> FlowEdits.editFor(context, binding)
            }
        return when (val result = edit?.let { WorkspaceEditor.apply(context.workspace, it, context.edit) }) {
            is EditResult.Applied -> FlowOutcome.Ready(checkNotNull(edit), result.workspace, saved)
            is EditResult.Rejected -> FlowOutcome.Blocked(result.reason)
            null -> FlowOutcome.Blocked(null, problem)
        }
    }

    internal fun bindingOf(state: BindingFlowState): LensBinding? {
        val lens = lens(state) ?: return null
        return state.expression?.let { LensBinding(lens, it, state.ref) }
    }
}

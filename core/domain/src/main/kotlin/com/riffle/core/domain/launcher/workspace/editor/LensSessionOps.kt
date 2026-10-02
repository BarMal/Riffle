package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SavedLens

/** The offer made after Save as lens: [count] other containers hold exactly the lens that was just saved as [name]. */
data class AdoptOffer(
    val id: LensId,
    val name: String,
    val count: Int,
)

sealed interface FlowCommit {
    /** [session] holds the flow's edit (and any new saved lens) as one undo step. [offer] is null when N = 0. */
    data class Applied(
        val session: WorkspaceEditSession,
        val offer: AdoptOffer? = null,
    ) : FlowCommit

    data class Blocked(
        val rejection: EditRejection?,
        val problem: LibraryProblem? = null,
    ) : FlowCommit
}

/**
 * The saved-lens operations of the editor on a [WorkspaceEditSession]: confirm a flow (with Save as lens), adopt the
 * identical containers, and detach. Each applies as exactly one history step, so Undo reverts it whole, and each
 * goes through the same gates as every other edit ([WorkspaceEditor.apply], `LensLibraryEditor`).
 */
object LensSessionOps {
    /**
     * Confirms the flow in [state]. [context] must be built over [session]'s draft and scope. The edit and, when
     * Save as lens was asked for, the new library entry are one step. The offer is made only when N > 0.
     */
    fun commit(
        session: WorkspaceEditSession,
        state: BindingFlowState,
        context: BindingFlowContext,
    ): FlowCommit =
        when (val outcome = BindingFlow.confirm(state, context)) {
            is FlowOutcome.Blocked -> FlowCommit.Blocked(outcome.rejection, outcome.problem)
            is FlowOutcome.Ready -> {
                val scope = outcome.saved?.let { session.scope.copy(library = it.library) } ?: session.scope
                val step = session.apply(outcome.edit, context.edit, scope)
                when (val result = step.result) {
                    is EditResult.Rejected -> FlowCommit.Blocked(result.reason)
                    is EditResult.Applied -> FlowCommit.Applied(step.session, offerFor(step.session, outcome.saved))
                }
            }
        }

    /** The binding at [target] becomes inline again (always valid, a no-op for an inline binding). */
    fun detach(
        session: WorkspaceEditSession,
        target: FlowMode,
        context: EditContext = EditContext(),
    ): WorkspaceEditSession =
        when (
            val result =
                LensLibraryEditor.detach(
                    session.scope.layoutWith(session.draft),
                    session.draft.id,
                    target,
                    context,
                )
        ) {
            is LibraryEditResult.Applied -> session.applyLayout(result.layout)
            is LibraryEditResult.Rejected -> session
        }

    /** The identical containers of saved lens [id] now use it: one step, one Undo. */
    fun adopt(
        session: WorkspaceEditSession,
        id: LensId,
    ): WorkspaceEditSession = session.applyLayout(LensAdoption.adopt(session.scope.layoutWith(session.draft), id))

    /** The saved lens the binding at [target] uses, or null when it is inline or its reference no longer resolves. */
    fun savedLensAt(
        session: WorkspaceEditSession,
        target: FlowMode,
    ): SavedLens? = FlowEdits.existingBinding(session.draft, target)?.ref?.let(session.scope.library::find)

    /** How many other containers [adopt] would take over now (0 when there is nothing to offer). */
    fun identicalCount(
        session: WorkspaceEditSession,
        id: LensId,
    ): Int = LensAdoption.identical(session.scope.layoutWith(session.draft), id).size

    private fun offerFor(
        session: WorkspaceEditSession,
        saved: SavedLensPlan?,
    ): AdoptOffer? =
        saved?.let { identicalCount(session, it.id) }?.takeIf { it > 0 }?.let { AdoptOffer(saved.id, saved.name, it) }
}

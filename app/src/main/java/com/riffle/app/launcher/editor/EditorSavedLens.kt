package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.editor.FlowCommit
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensSessionOps
import com.riffle.core.domain.launcher.workspace.editor.SavedLensFlow
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEditSession

/**
 * The editor state machine's saved-lens steps, kept out of [WorkspaceEditorReducer]: confirming a flow (with Save as
 * lens), the offer to adopt identical containers, detaching, and Undo or Redo while a flow is open. Each one is a
 * domain operation on the session ([LensSessionOps]), so it is one history step and goes through the usual gates.
 */
internal object EditorSavedLens {
    /** The actions this object owns, or null for any other. Undo and Redo are here: they reconcile an open flow. */
    fun reduce(
        state: WorkspaceEditorUiState,
        action: EditorAction,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState? =
        when (action) {
            EditorAction.Undo -> history(state, state.session.undo(), environment)
            EditorAction.Redo -> history(state, state.session.redo(), environment)
            is EditorAction.Detach -> detach(state, action.mode, environment)
            EditorAction.AdoptIdentical -> adopt(state)
            EditorAction.DismissOffer -> state.copy(offer = null)
            else -> null
        }

    /** Confirms the flow. A refusal keeps the flow open and says why (the editor's reason, or the library's). */
    fun commit(
        state: WorkspaceEditorUiState,
        flow: ActiveFlow,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState {
        val context = environment.flowContext(state.session, flow.mode)
        return when (val result = LensSessionOps.commit(state.session, flow.state, context)) {
            is FlowCommit.Applied ->
                state.copy(
                    session = result.session,
                    flow = null,
                    message = savedMessage(state, flow, result),
                    offer = result.offer,
                )
            is FlowCommit.Blocked ->
                state.copy(
                    message =
                        result.problem?.let { EditorMessage.LibraryRefused(it) }
                            ?: result.rejection?.let { EditorMessage.Rejected(it) },
                )
        }
    }

    /** Detaches the container at [mode] from its saved lens (it keeps drawing the same lens, no longer shared). */
    fun detach(
        state: WorkspaceEditorUiState,
        mode: FlowMode,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState {
        val saved = LensSessionOps.savedLensAt(state.session, mode)
        val next = LensSessionOps.detach(state.session, mode, environment.editContext)
        return if (saved == null || next == state.session) {
            state.copy(message = null)
        } else {
            state.copy(session = next, message = EditorMessage.Detached(saved.name))
        }
    }

    /** Accepts the offer: every identical container uses the new saved lens, as one history step. */
    fun adopt(state: WorkspaceEditorUiState): WorkspaceEditorUiState {
        val offer = state.offer ?: return state
        val count = LensSessionOps.identicalCount(state.session, offer.id)
        val next = LensSessionOps.adopt(state.session, offer.id)
        return if (next == state.session) {
            state.copy(offer = null)
        } else {
            state.copy(session = next, offer = null, message = EditorMessage.Adopted(offer.name, count))
        }
    }

    /**
     * Undo or Redo: [next] becomes the session. A saved lens an open flow was using may be gone now (Undo removed it);
     * the flow then keeps the lens as its own, so it can never confirm a reference that does not resolve.
     */
    fun history(
        state: WorkspaceEditorUiState,
        next: WorkspaceEditSession,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState {
        val flow =
            state.flow?.let { active ->
                val context = environment.flowContext(next, active.mode)
                active.copy(state = SavedLensFlow.reconcile(active.state, context))
            }
        return state.copy(session = next, flow = flow, message = null)
    }

    /** "Saved as lens" is announced when the flow created a saved lens and there is no offer to say it in. */
    private fun savedMessage(
        state: WorkspaceEditorUiState,
        flow: ActiveFlow,
        result: FlowCommit.Applied,
    ): EditorMessage? =
        flow.state.saveAs?.takeIf {
            result.offer == null && result.session.scope.library != state.session.scope.library
        }?.let { EditorMessage.SavedAsLens(it.name.trim()) }
}

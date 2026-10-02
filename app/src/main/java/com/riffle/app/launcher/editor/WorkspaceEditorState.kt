package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.editor.AdoptOffer
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowReducer
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.CommitResult
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.EditRejection
import com.riffle.core.domain.launcher.workspace.editor.EditResult
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.LensScopeChange
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEditSession

/** What the editor reads that is not its own state. */
internal data class EditorEnvironment(
    val sources: List<SourceChoice>,
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
) {
    val editContext: EditContext get() = EditContext(capabilities, sources.map { it.descriptor })

    fun flowContext(
        workspace: Workspace,
        mode: FlowMode,
    ): BindingFlowContext = BindingFlowContext(sources, workspace, mode, capabilities, ids)

    /** The flow's context over [session]: its draft, and the layout's saved lenses and other workspaces. */
    fun flowContext(
        session: WorkspaceEditSession,
        mode: FlowMode,
    ): BindingFlowContext = BindingFlowContext(sources, session.draft, mode, capabilities, ids, session.scope)
}

/** A running Source -> Expression -> Container -> Confirm flow. */
internal data class ActiveFlow(
    val mode: FlowMode,
    val state: BindingFlowState,
)

/** A one-line notice the screen shows and the user can dismiss. */
internal sealed interface EditorMessage {
    data class Rejected(val rejection: EditRejection) : EditorMessage

    data class SaveBlocked(val issues: List<WorkspaceIssue>) : EditorMessage

    /** Save as lens made [name] and no other container holds the same lens. */
    data class SavedAsLens(val name: String) : EditorMessage

    data class Adopted(val name: String, val count: Int) : EditorMessage

    data class Detached(val name: String) : EditorMessage

    /** Something the user asked for was refused by the library (the name, a full library). */
    data class LibraryRefused(val problem: LibraryProblem) : EditorMessage
}

internal data class WorkspaceEditorUiState(
    val session: WorkspaceEditSession,
    val flow: ActiveFlow? = null,
    val message: EditorMessage? = null,
    /** Closing with unsaved changes asks first. */
    val confirmingDiscard: Boolean = false,
    /** After Save as lens: the other containers that hold the same lens, which can use it too. Null: no offer. */
    val offer: AdoptOffer? = null,
)

internal sealed interface EditorAction {
    data class Apply(val edit: WorkspaceEdit) : EditorAction

    data object Undo : EditorAction

    data object Redo : EditorAction

    /** Throws away every unsaved change but stays in the editor. */
    data object RevertChanges : EditorAction

    data object Save : EditorAction

    data object RequestClose : EditorAction

    data object DiscardAndClose : EditorAction

    data object KeepEditing : EditorAction

    data object DismissMessage : EditorAction

    /** Makes the container at [mode] use its own copy of the saved lens again (Undo reattaches it). */
    data class Detach(val mode: FlowMode) : EditorAction

    /** Accepts the offer after Save as lens: the identical containers use the new saved lens, as one step. */
    data object AdoptIdentical : EditorAction

    data object DismissOffer : EditorAction

    data class StartFlow(val mode: FlowMode) : EditorAction

    data class Flow(val action: BindingFlowAction) : EditorAction

    data object ConfirmFlow : EditorAction

    data object CancelFlow : EditorAction

    /** The system back: steps back inside a flow, cancels a flow at its first step, otherwise asks to close. */
    data object Back : EditorAction
}

/** What the host must do as a result of an action. */
internal sealed interface EditorEffect {
    /** [change] is what lies outside [workspace]: the new library and the other workspaces that changed. */
    data class Save(
        val workspace: Workspace,
        val change: LensScopeChange = LensScopeChange(),
    ) : EditorEffect

    data object Close : EditorEffect
}

internal data class EditorTransition(
    val state: WorkspaceEditorUiState,
    val effect: EditorEffect? = null,
)

/**
 * The editor's state machine: pure `(state, action) -> state + effect`, so it is unit-tested on the JVM and the
 * composables only render state and forward actions. All validity decisions come from the domain.
 */
internal class WorkspaceEditorReducer(private val environment: EditorEnvironment) {
    /** [scope] is the layout's library and other workspaces; empty for a host without saved lenses. */
    fun start(
        workspace: Workspace,
        scope: LensScope = LensScope(),
    ): WorkspaceEditorUiState = WorkspaceEditorUiState(WorkspaceEditSession(workspace, scope = scope))

    /** An offer lasts for one step: any action other than answering it ends it. */
    fun reduce(
        state: WorkspaceEditorUiState,
        action: EditorAction,
    ): EditorTransition {
        val transition =
            EditorSavedLens.reduce(state, action, environment)?.let { EditorTransition(it) }
                ?: step(state, action)
        val answers = action == EditorAction.ConfirmFlow || action == EditorAction.AdoptIdentical
        return if (answers || transition.state.offer == null) {
            transition
        } else {
            transition.copy(state = transition.state.copy(offer = null))
        }
    }

    private fun step(
        state: WorkspaceEditorUiState,
        action: EditorAction,
    ): EditorTransition =
        when (action) {
            is EditorAction.Apply -> EditorTransition(apply(state, action.edit))
            EditorAction.RevertChanges -> EditorTransition(WorkspaceEditorUiState(state.session.cancel()))
            EditorAction.Save -> save(state)
            EditorAction.RequestClose -> requestClose(state)
            EditorAction.DiscardAndClose ->
                EditorTransition(
                    WorkspaceEditorUiState(state.session.cancel()),
                    EditorEffect.Close,
                )
            EditorAction.KeepEditing -> EditorTransition(state.copy(confirmingDiscard = false))
            EditorAction.DismissMessage -> EditorTransition(state.copy(message = null))
            EditorAction.Back -> back(state)
            is EditorAction.StartFlow, is EditorAction.Flow, EditorAction.ConfirmFlow, EditorAction.CancelFlow ->
                EditorTransition(reduceFlow(state, action))
            else -> EditorTransition(state)
        }

    private fun apply(
        state: WorkspaceEditorUiState,
        edit: WorkspaceEdit,
    ): WorkspaceEditorUiState {
        val step = state.session.apply(edit, environment.editContext)
        val message = (step.result as? EditResult.Rejected)?.let { EditorMessage.Rejected(it.reason) }
        return state.copy(session = step.session, message = message)
    }

    private fun save(state: WorkspaceEditorUiState): EditorTransition =
        when (val result = state.session.commit(environment.editContext)) {
            is CommitResult.Committed ->
                EditorTransition(
                    WorkspaceEditorUiState(result.session),
                    if (state.session.isDirty) {
                        EditorEffect.Save(
                            result.session.committed,
                            result.session.scope.changeFrom(state.session.committedScope),
                        )
                    } else {
                        EditorEffect.Close
                    },
                )
            is CommitResult.Rejected ->
                EditorTransition(state.copy(message = EditorMessage.SaveBlocked(result.issues)))
        }

    private fun requestClose(state: WorkspaceEditorUiState): EditorTransition =
        if (state.session.isDirty) {
            EditorTransition(state.copy(confirmingDiscard = true))
        } else {
            EditorTransition(state, EditorEffect.Close)
        }

    private fun back(state: WorkspaceEditorUiState): EditorTransition {
        val flow = state.flow
        return when {
            state.confirmingDiscard -> EditorTransition(state.copy(confirmingDiscard = false))
            flow == null -> requestClose(state)
            flow.state.step == EditorStep.SOURCE -> EditorTransition(state.copy(flow = null, message = null))
            else -> EditorTransition(reduceFlow(state, EditorAction.Flow(BindingFlowAction.Back)))
        }
    }

    private fun reduceFlow(
        state: WorkspaceEditorUiState,
        action: EditorAction,
    ): WorkspaceEditorUiState =
        when (action) {
            is EditorAction.StartFlow -> {
                val context = environment.flowContext(state.session, action.mode)
                state.copy(flow = ActiveFlow(action.mode, BindingFlow.start(context)), message = null)
            }
            is EditorAction.Flow -> {
                val flow = state.flow
                if (flow == null) {
                    state
                } else {
                    val context = environment.flowContext(state.session, flow.mode)
                    val next = BindingFlowReducer.reduce(flow.state, action.action, context)
                    state.copy(flow = flow.copy(state = next), message = null)
                }
            }
            EditorAction.ConfirmFlow -> confirmFlow(state)
            else -> state.copy(flow = null, message = null)
        }

    private fun confirmFlow(state: WorkspaceEditorUiState): WorkspaceEditorUiState {
        val flow = state.flow ?: return state
        return EditorSavedLens.commit(state, flow, environment)
    }
}

/** A refusal reads as an error; saved-lens confirmations do not. */
internal fun messageIsError(message: EditorMessage): Boolean =
    when (message) {
        is EditorMessage.Rejected, is EditorMessage.SaveBlocked, is EditorMessage.LibraryRefused -> true
        is EditorMessage.SavedAsLens, is EditorMessage.Adopted, is EditorMessage.Detached -> false
    }

internal fun messageText(message: EditorMessage): String =
    when (message) {
        is EditorMessage.Rejected -> EditorReasonText.rejection(message.rejection)
        is EditorMessage.SavedAsLens -> EditorLensMessages.savedAsLens(message.name)
        is EditorMessage.Adopted -> EditorLensMessages.adopted(message.name, message.count)
        is EditorMessage.Detached -> EditorLensMessages.detached(message.name)
        is EditorMessage.LibraryRefused -> EditorLensMessages.libraryProblem(message.problem)
        is EditorMessage.SaveBlocked ->
            message.issues.map {
                EditorReasonText.workspace(
                    it,
                )
            }.distinct().joinToString("; ")
    }

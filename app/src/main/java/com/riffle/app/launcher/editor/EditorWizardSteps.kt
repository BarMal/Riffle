package com.riffle.app.launcher.editor

import com.riffle.app.launcher.wizard.WizardPlacement
import com.riffle.app.launcher.wizard.WizardStores
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.EditResult
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry

/**
 * What the content wizard needs inside the editor besides the session: the pool and the home's rules ([stores], for
 * Favourite apps; null: picked apps cannot be placed and say so), where a new arrangement page goes ([placement]),
 * and what the pickers offer ([apps], [feeds]).
 */
class EditorWizardSupport internal constructor(
    internal val stores: WizardStores? = null,
    internal val placement: WizardPlacement = WizardPlacement(HomeLayoutDeviceClass.PHONE),
    internal val apps: List<AppPick> = emptyList(),
    internal val feeds: List<FeedOption> = emptyList(),
)

/** The wizard's context over the editor session: the draft as the workspace, the session's library and others. */
internal fun editorWizardContext(
    state: WorkspaceEditorUiState,
    open: EditorWizard,
    environment: EditorEnvironment,
    feeds: List<FeedOption> = emptyList(),
): WizardContext {
    val session = state.session
    return WizardContext(
        entry = WizardEntry.Editor(session.draft.id, open.pageId),
        sources = environment.sources,
        layout = session.scope.layoutWith(session.draft),
        capabilities = environment.capabilities,
        feeds = feeds,
    )
}

/**
 * The editor state machine's content wizard steps, kept out of [WorkspaceEditorReducer] like `EditorSavedLens`:
 * opening and closing the wizard, taking its result into the session as one history step (with the library it
 * leaves, so Undo, Redo, Revert and the discard prompt cover a saved lens too), and the advanced builder handoff.
 */
internal object EditorWizardSteps {
    /** The actions this object owns, or null for any other. */
    fun reduce(
        state: WorkspaceEditorUiState,
        action: EditorAction,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState? =
        when (action) {
            is EditorAction.OpenWizard -> state.copy(wizard = EditorWizard(action.pageId), flow = null, message = null)
            EditorAction.CloseWizard -> state.copy(wizard = null)
            is EditorAction.ApplyWizard -> applyWizard(state, action, environment)
            is EditorAction.StartAdvanced -> startAdvanced(state, action.handoff)
            else -> null
        }

    private fun applyWizard(
        state: WorkspaceEditorUiState,
        action: EditorAction.ApplyWizard,
        environment: EditorEnvironment,
    ): WorkspaceEditorUiState {
        val session = state.session
        val nextScope = session.scope.copy(library = action.library)
        val edit = action.edit
        if (edit == null) {
            // Only picked apps were placed (the pool saved them); a library change would still be one step.
            val next =
                if (nextScope == session.scope) session else session.applyLayout(nextScope.layoutWith(session.draft))
            return state.copy(session = next, wizard = null, message = EditorMessage.WizardAdded(action.name))
        }
        val step = session.apply(edit, environment.editContext, nextScope)
        val message =
            (step.result as? EditResult.Rejected)?.let { EditorMessage.Rejected(it.reason) }
                ?: EditorMessage.WizardAdded(action.name)
        return state.copy(session = step.session, wizard = null, message = message)
    }

    private fun startAdvanced(
        state: WorkspaceEditorUiState,
        handoff: AdvancedHandoff,
    ): WorkspaceEditorUiState {
        val flow =
            BindingFlowState(draft = handoff.draft, expression = handoff.expression, container = handoff.container)
        return state.copy(wizard = null, flow = ActiveFlow(FlowMode.Add, flow), message = null)
    }
}

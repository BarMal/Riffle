package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

enum class EditorStep {
    SOURCE,
    EXPRESSION,
    CONTAINER,
    CONFIRM,
}

/** What the flow is for: adding a new container, or re-binding one that exists (which skips the Container step). */
sealed interface FlowMode {
    data object Add : FlowMode

    data class EditPage(val pageId: ContainerId) : FlowMode

    data class EditWidget(val pageId: ContainerId, val widgetId: ContainerId) : FlowMode

    data object EditDock : FlowMode
}

/** Everything the flow reads that is not part of its own state. */
data class BindingFlowContext(
    val sources: List<SourceChoice>,
    val workspace: Workspace,
    val mode: FlowMode = FlowMode.Add,
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    /** The layout's saved lenses and other workspaces (empty for a host without saved lenses). */
    val scope: LensScope = LensScope(),
) {
    val edit: EditContext get() = EditContext(capabilities, sources.map { it.descriptor })

    /** The layout the saved-lens operations work on: [workspace] (the draft) with [scope] around it. */
    val layout: LayoutWorkspaces get() = scope.layoutWith(workspace)
}

/** Save as lens, asked for on the Confirm step: the name the new saved lens will get. */
data class SaveAsDraft(val name: String)

/** The user's choices so far. Selections are always consistent with the options at the current lens. */
data class BindingFlowState(
    val step: EditorStep = EditorStep.SOURCE,
    val draft: LensDraft = LensDraft(),
    val expression: ExpressionKind? = null,
    val container: ContainerKind? = null,
    val widgetTarget: WidgetTarget? = null,
    /**
     * The saved lens this binding uses. While it is set the draft equals that library lens and the lens builder is
     * closed: changing the lens means detaching first (or changing the saved lens in Settings > Saved lenses).
     */
    val ref: LensId? = null,
    /** The Source step shows the saved-lens list instead of the builder. */
    val choosingSaved: Boolean = false,
    /** Save as lens was asked for on Confirm; null when it was not. */
    val saveAs: SaveAsDraft? = null,
)

sealed interface BindingFlowAction {
    data class ToggleSource(val id: SourceId) : BindingFlowAction

    data class ApplyPreset(val preset: LensPreset) : BindingFlowAction

    data class SetFilter(val filter: LensFilter) : BindingFlowAction

    data class SetGroup(val group: LensGroup) : BindingFlowAction

    data class SetSort(val sort: LensSort) : BindingFlowAction

    data class SetLimit(val limit: Int?) : BindingFlowAction

    /** Sets the query of [source] on the draft; blank clears it. Ignored for a source that takes no query. */
    data class SetQuery(val source: SourceId, val text: String) : BindingFlowAction

    data class PickExpression(val kind: ExpressionKind) : BindingFlowAction

    /** [target] is only read for widgets; null picks the first accepted place. */
    data class PickContainer(val kind: ContainerKind, val target: WidgetTarget? = null) : BindingFlowAction

    /** Opens the saved-lens list on the Source step. */
    data object ShowSavedLenses : BindingFlowAction

    /** Back from the saved-lens list to the builder. */
    data object ShowBuilder : BindingFlowAction

    /** Uses saved lens [id]: the draft becomes its lens and the binding keeps the reference. Ignored if not valid. */
    data class UseSavedLens(val id: LensId) : BindingFlowAction

    /** Makes the binding's lens its own again (Source step); the draft keeps the lens. */
    data object DetachSavedLens : BindingFlowAction

    /** Asks for Save as lens on Confirm; [base] is the app's suggested name, made unique by the library. */
    data class StartSaveAsLens(val base: String) : BindingFlowAction

    data object CancelSaveAsLens : BindingFlowAction

    data class SetSaveAsName(val name: String) : BindingFlowAction

    data object Next : BindingFlowAction

    data object Back : BindingFlowAction
}

/** A saved lens the confirmed flow creates: apply it with the edit so the binding's reference resolves. */
data class SavedLensPlan(
    val library: LensLibrary,
    val id: LensId,
    val name: String,
)

sealed interface FlowOutcome {
    /**
     * [edit] is accepted: apply it to the session. [result] is the workspace it produces. [saved] is set when the
     * flow also creates a saved lens: its [SavedLensPlan.library] is the scope's next library, in the same step.
     */
    data class Ready(
        val edit: WorkspaceEdit,
        val result: Workspace,
        val saved: SavedLensPlan? = null,
    ) : FlowOutcome

    /** [problem] is why the Save as lens name or the library refuses; [rejection] is the editor's reason. */
    data class Blocked(
        val rejection: EditRejection?,
        val problem: LibraryProblem? = null,
    ) : FlowOutcome
}

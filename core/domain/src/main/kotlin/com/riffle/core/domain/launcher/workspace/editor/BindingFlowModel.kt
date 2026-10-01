package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
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
) {
    val edit: EditContext get() = EditContext(capabilities, sources.map { it.descriptor })
}

/** The user's choices so far. Selections are always consistent with the options at the current lens. */
data class BindingFlowState(
    val step: EditorStep = EditorStep.SOURCE,
    val draft: LensDraft = LensDraft(),
    val expression: ExpressionKind? = null,
    val container: ContainerKind? = null,
    val widgetTarget: WidgetTarget? = null,
)

sealed interface BindingFlowAction {
    data class ToggleSource(val id: SourceId) : BindingFlowAction

    data class ApplyPreset(val preset: LensPreset) : BindingFlowAction

    data class SetFilter(val filter: LensFilter) : BindingFlowAction

    data class SetGroup(val group: LensGroup) : BindingFlowAction

    data class SetSort(val sort: LensSort) : BindingFlowAction

    data class SetLimit(val limit: Int?) : BindingFlowAction

    data class PickExpression(val kind: ExpressionKind) : BindingFlowAction

    /** [target] is only read for widgets; null picks the first accepted place. */
    data class PickContainer(val kind: ContainerKind, val target: WidgetTarget? = null) : BindingFlowAction

    data object Next : BindingFlowAction

    data object Back : BindingFlowAction
}

sealed interface FlowOutcome {
    /** [edit] is accepted: apply it to the session. [result] is the workspace it produces. */
    data class Ready(val edit: WorkspaceEdit, val result: Workspace) : FlowOutcome

    data class Blocked(val rejection: EditRejection?) : FlowOutcome
}

package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue

/** One user-visible change to a [Workspace]. Applied by [WorkspaceEditor], which rejects invalid results. */
sealed interface WorkspaceEdit {
    /** Inserts [page] at [index] (appended when null; clamped to the list). */
    data class AddPage(val page: PageHost, val index: Int? = null) : WorkspaceEdit

    data class RemovePage(val pageId: ContainerId) : WorkspaceEdit

    /** Moves a page to [toIndex] (clamped). The button equivalent of drag-reordering. */
    data class MovePage(val pageId: ContainerId, val toIndex: Int) : WorkspaceEdit

    /** Replaces the lens + expression of a bound page or page-set. Grid pages have no page binding. */
    data class SetPageBinding(val pageId: ContainerId, val binding: LensBinding) : WorkspaceEdit

    data class AddWidget(
        val pageId: ContainerId,
        val widget: WidgetContainer,
        val column: Int,
        val row: Int,
    ) : WorkspaceEdit

    data class MoveWidget(
        val pageId: ContainerId,
        val widgetId: ContainerId,
        val column: Int,
        val row: Int,
    ) : WorkspaceEdit

    data class ResizeWidget(
        val pageId: ContainerId,
        val widgetId: ContainerId,
        val span: WidgetSpan,
    ) : WorkspaceEdit

    data class SetWidgetBinding(
        val pageId: ContainerId,
        val widgetId: ContainerId,
        val binding: LensBinding,
    ) : WorkspaceEdit

    data class RemoveWidget(val pageId: ContainerId, val widgetId: ContainerId) : WorkspaceEdit

    data class Rename(val name: String) : WorkspaceEdit

    /** Sets (or clears, when null) the dock's dynamic section lens. */
    data class SetDockSection(val binding: LensBinding?) : WorkspaceEdit

    /** Sets (or clears, when null) the per-workspace skin override. */
    data class SetSkinOverride(val skinId: String?) : WorkspaceEdit
}

sealed interface EditRejection {
    /** The edit would leave the workspace with problems it did not have before. */
    data class Invalid(val issues: List<WorkspaceIssue>) : EditRejection

    data class UnknownPage(val id: ContainerId) : EditRejection

    data class UnknownWidget(val id: ContainerId) : EditRejection

    /** A widget edit named a page that is not a widget grid. */
    data class NotAGridPage(val id: ContainerId) : EditRejection

    /** A page-binding edit named a widget grid, which has no binding of its own. */
    data class NotABoundPage(val id: ContainerId) : EditRejection

    data object BlankName : EditRejection

    data object BlankSkinId : EditRejection
}

sealed interface EditResult {
    data class Applied(val workspace: Workspace) : EditResult

    data class Rejected(val reason: EditRejection) : EditResult
}

/** What the layout can draw and which sources exist. Defaults accept everything and skip source checks. */
data class EditContext(
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    val sources: List<SourceDescriptor>? = null,
)

package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.Workspace

/** Where a new binding can live. [FINDER_PAGE] is a [PAGE] with the Finder role; [DOCK_SECTION] is the dock's lens. */
enum class ContainerKind {
    WIDGET,
    PAGE,
    FINDER_PAGE,
    PAGE_SET,
    DOCK_SECTION,
}

/** A place for a widget: an existing grid page with room, or [pageId] null for a new widget page. */
data class WidgetTarget(
    val pageId: ContainerId?,
    val slot: WidgetSlot,
)

/**
 * One entry of the Container step. [enabled] is true exactly when the editor would accept the edit, so the
 * UI and the domain cannot disagree; a disabled entry carries the [rejection] to explain why.
 */
data class ContainerChoice(
    val kind: ContainerKind,
    val enabled: Boolean,
    val rejection: EditRejection? = null,
    /** Widget only: every place the widget could go. Empty when disabled. */
    val widgetTargets: List<WidgetTarget> = emptyList(),
)

/** Builds the workspace edit that puts a binding in a container. */
object ContainerEdits {
    /** Ids used while only testing whether an edit would be accepted. */
    private val candidatePage = ContainerId("editor.candidate.page")
    private val candidateWidget = ContainerId("editor.candidate.widget")

    fun build(
        kind: ContainerKind,
        binding: LensBinding,
        target: WidgetTarget?,
        pageId: ContainerId = candidatePage,
        widgetId: ContainerId = candidateWidget,
    ): WorkspaceEdit =
        when (kind) {
            ContainerKind.PAGE -> WorkspaceEdit.AddPage(PageContainer(pageId, PageContent.Bound(binding)))
            ContainerKind.FINDER_PAGE ->
                WorkspaceEdit.AddPage(PageContainer(pageId, PageContent.Bound(binding), PageRole.FINDER))
            ContainerKind.PAGE_SET -> WorkspaceEdit.AddPage(PageSetContainer(pageId, binding))
            ContainerKind.DOCK_SECTION -> WorkspaceEdit.SetDockSection(binding)
            ContainerKind.WIDGET -> widgetEdit(binding, target, pageId, widgetId)
        }

    private fun widgetEdit(
        binding: LensBinding,
        target: WidgetTarget?,
        pageId: ContainerId,
        widgetId: ContainerId,
    ): WorkspaceEdit {
        val slot = target?.slot ?: WidgetSlot(0, 0)
        val columns = WidgetSlots.DEFAULT_COLUMNS
        val widget = WidgetContainer(widgetId, WidgetSlots.defaultSpan(binding.expression, columns), binding)
        val existing = target?.pageId
        return if (existing == null) {
            val grid = PageContent.WidgetGrid(columns, WidgetSlots.DEFAULT_ROWS, listOf(WidgetPlacement(widget, 0, 0)))
            WorkspaceEdit.AddPage(PageContainer(pageId, grid))
        } else {
            WorkspaceEdit.AddWidget(existing, widget, slot.column, slot.row)
        }
    }
}

object ContainerOptions {
    /** Every container kind for [binding], enabled only when the editor would accept it. */
    fun forBinding(
        binding: LensBinding,
        workspace: Workspace,
        context: EditContext = EditContext(),
    ): List<ContainerChoice> =
        ContainerKind.entries.map { kind ->
            if (kind == ContainerKind.WIDGET) {
                widgetChoice(binding, workspace, context)
            } else {
                val result = WorkspaceEditor.apply(workspace, ContainerEdits.build(kind, binding, null), context)
                ContainerChoice(kind, result is EditResult.Applied, (result as? EditResult.Rejected)?.reason)
            }
        }

    /** The widget places that would be accepted: grid pages with a free cell, then a new page. */
    fun widgetTargets(
        binding: LensBinding,
        workspace: Workspace,
        context: EditContext = EditContext(),
    ): List<WidgetTarget> {
        val span = WidgetSlots.defaultSpan(binding.expression)
        val existing =
            workspace.pages.mapNotNull { page ->
                val grid = (page as? PageContainer)?.content as? PageContent.WidgetGrid
                val slot = grid?.let { WidgetSlots.firstFree(it, span) }
                slot?.let { WidgetTarget(page.id, it) }
            }
        return (existing + WidgetTarget(null, WidgetSlot(0, 0))).filter { target ->
            val edit = ContainerEdits.build(ContainerKind.WIDGET, binding, target)
            WorkspaceEditor.apply(workspace, edit, context) is EditResult.Applied
        }
    }

    private fun widgetChoice(
        binding: LensBinding,
        workspace: Workspace,
        context: EditContext,
    ): ContainerChoice {
        val targets = widgetTargets(binding, workspace, context)
        if (targets.isNotEmpty()) return ContainerChoice(ContainerKind.WIDGET, true, widgetTargets = targets)
        val rejection =
            (
                WorkspaceEditor.apply(
                    workspace,
                    ContainerEdits.build(ContainerKind.WIDGET, binding, WidgetTarget(null, WidgetSlot(0, 0))),
                    context,
                ) as? EditResult.Rejected
            )?.reason
        return ContainerChoice(ContainerKind.WIDGET, false, rejection)
    }
}

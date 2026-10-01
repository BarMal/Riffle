package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.Workspace

/** Widget edits inside a grid page. Bounds, overlap and pairing are enforced by the editor's validation gate. */
internal object GridEdits {
    fun transform(
        workspace: Workspace,
        edit: WorkspaceEdit,
    ): EditResult {
        val pageId = pageIdOf(edit)
        val page = workspace.pages.firstOrNull { it.id == pageId }
        val grid = (page as? PageContainer)?.content as? PageContent.WidgetGrid
        val placements = grid?.let { placementsAfter(it.placements, edit) }
        return when {
            page == null -> EditResult.Rejected(EditRejection.UnknownPage(pageId))
            page !is PageContainer || grid == null -> EditResult.Rejected(EditRejection.NotAGridPage(pageId))
            placements == null -> EditResult.Rejected(EditRejection.UnknownWidget(widgetIdOf(edit)))
            else -> {
                val updated = page.copy(content = grid.copy(placements = placements))
                EditResult.Applied(workspace.copy(pages = workspace.pages.map { if (it.id == pageId) updated else it }))
            }
        }
    }

    private fun pageIdOf(edit: WorkspaceEdit): ContainerId =
        when (edit) {
            is WorkspaceEdit.AddWidget -> edit.pageId
            is WorkspaceEdit.MoveWidget -> edit.pageId
            is WorkspaceEdit.ResizeWidget -> edit.pageId
            is WorkspaceEdit.SetWidgetBinding -> edit.pageId
            is WorkspaceEdit.RemoveWidget -> edit.pageId
            else -> error("Not a widget edit: $edit")
        }

    private fun widgetIdOf(edit: WorkspaceEdit): ContainerId =
        when (edit) {
            is WorkspaceEdit.AddWidget -> edit.widget.id
            is WorkspaceEdit.MoveWidget -> edit.widgetId
            is WorkspaceEdit.ResizeWidget -> edit.widgetId
            is WorkspaceEdit.SetWidgetBinding -> edit.widgetId
            is WorkspaceEdit.RemoveWidget -> edit.widgetId
            else -> error("Not a widget edit: $edit")
        }

    /** Null when the edit names a widget the grid does not hold. */
    private fun placementsAfter(
        placements: List<WidgetPlacement>,
        edit: WorkspaceEdit,
    ): List<WidgetPlacement>? =
        when (edit) {
            is WorkspaceEdit.AddWidget -> placements + WidgetPlacement(edit.widget, edit.column, edit.row)
            is WorkspaceEdit.MoveWidget ->
                placements.changed(edit.widgetId) { it.copy(column = edit.column, row = edit.row) }
            is WorkspaceEdit.ResizeWidget ->
                placements.changed(edit.widgetId) { it.copy(widget = it.widget.copy(span = edit.span)) }
            is WorkspaceEdit.SetWidgetBinding ->
                placements.changed(edit.widgetId) { it.copy(widget = it.widget.copy(binding = edit.binding)) }
            is WorkspaceEdit.RemoveWidget ->
                if (placements.any { it.widget.id == edit.widgetId }) {
                    placements.filterNot { it.widget.id == edit.widgetId }
                } else {
                    null
                }
            else -> error("Not a widget edit: $edit")
        }

    private fun List<WidgetPlacement>.changed(
        id: ContainerId,
        change: (WidgetPlacement) -> WidgetPlacement,
    ): List<WidgetPlacement>? =
        if (none {
                it.widget.id == id
            }
        ) {
            null
        } else {
            map { if (it.widget.id == id) change(it) else it }
        }
}

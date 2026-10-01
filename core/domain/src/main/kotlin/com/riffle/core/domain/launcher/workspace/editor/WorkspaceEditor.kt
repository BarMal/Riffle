package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation

/**
 * Pure editor operations. Every edit returns a new [Workspace] or a rejection, never a half-applied state.
 *
 * The gate: an edit is rejected when its result has a [WorkspaceIssue] the workspace did not already have.
 * A valid workspace therefore stays valid under any edit sequence, and an already-broken one (stored data
 * from a newer build, say) can still be repaired step by step without being locked out.
 */
object WorkspaceEditor {
    fun apply(
        workspace: Workspace,
        edit: WorkspaceEdit,
        context: EditContext = EditContext(),
    ): EditResult =
        when (val result = transform(workspace, edit)) {
            is EditResult.Rejected -> result
            is EditResult.Applied -> gate(workspace, result.workspace, context)
        }

    /** Issues [workspace] has against [context]. */
    fun issues(
        workspace: Workspace,
        context: EditContext = EditContext(),
    ): List<WorkspaceIssue> = WorkspaceValidation.validate(workspace, context.capabilities, context.sources)

    private fun gate(
        before: Workspace,
        after: Workspace,
        context: EditContext,
    ): EditResult {
        val known = issues(before, context).toSet()
        val introduced = issues(after, context).filterNot { it in known }
        return if (introduced.isEmpty()) {
            EditResult.Applied(after)
        } else {
            EditResult.Rejected(EditRejection.Invalid(introduced))
        }
    }

    private fun transform(
        workspace: Workspace,
        edit: WorkspaceEdit,
    ): EditResult =
        when (edit) {
            is WorkspaceEdit.AddPage -> PageEdits.add(workspace, edit)
            is WorkspaceEdit.RemovePage -> PageEdits.remove(workspace, edit.pageId)
            is WorkspaceEdit.MovePage -> PageEdits.move(workspace, edit)
            is WorkspaceEdit.SetPageBinding -> PageEdits.setBinding(workspace, edit)
            is WorkspaceEdit.Rename -> rename(workspace, edit.name)
            is WorkspaceEdit.SetDockSection -> applied(workspace.copy(dock = WorkspaceDock(edit.binding)))
            is WorkspaceEdit.SetSkinOverride -> setSkin(workspace, edit.skinId)
            is WorkspaceEdit.AddWidget,
            is WorkspaceEdit.MoveWidget,
            is WorkspaceEdit.ResizeWidget,
            is WorkspaceEdit.SetWidgetBinding,
            is WorkspaceEdit.RemoveWidget,
            -> GridEdits.transform(workspace, edit)
        }

    private fun applied(workspace: Workspace): EditResult = EditResult.Applied(workspace)

    private fun rejected(reason: EditRejection): EditResult = EditResult.Rejected(reason)

    private fun rename(
        workspace: Workspace,
        name: String,
    ): EditResult =
        if (name.isBlank()) rejected(EditRejection.BlankName) else applied(workspace.copy(name = name.trim()))

    private fun setSkin(
        workspace: Workspace,
        skinId: String?,
    ): EditResult =
        if (skinId != null && skinId.isBlank()) {
            rejected(EditRejection.BlankSkinId)
        } else {
            applied(workspace.copy(skinOverrideId = skinId))
        }
}

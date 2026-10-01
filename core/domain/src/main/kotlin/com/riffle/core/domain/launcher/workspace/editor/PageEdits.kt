package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.Workspace

/** Page-level edits: add, remove, reorder and re-bind. Validity is enforced by the editor's gate. */
internal object PageEdits {
    fun add(
        workspace: Workspace,
        edit: WorkspaceEdit.AddPage,
    ): EditResult = EditResult.Applied(workspace.copy(pages = workspace.pages.inserted(edit.page, edit.index)))

    private fun List<PageHost>.inserted(
        page: PageHost,
        index: Int?,
    ): List<PageHost> = toMutableList().apply { add((index ?: size).coerceIn(0, size), page) }

    fun remove(
        workspace: Workspace,
        id: ContainerId,
    ): EditResult =
        if (workspace.pages.none { it.id == id }) {
            EditResult.Rejected(EditRejection.UnknownPage(id))
        } else {
            EditResult.Applied(workspace.copy(pages = workspace.pages.filterNot { it.id == id }))
        }

    fun move(
        workspace: Workspace,
        edit: WorkspaceEdit.MovePage,
    ): EditResult {
        val moving = workspace.pages.firstOrNull { it.id == edit.pageId }
        return if (moving == null) {
            EditResult.Rejected(EditRejection.UnknownPage(edit.pageId))
        } else {
            val without = workspace.pages.filterNot { it.id == edit.pageId }
            EditResult.Applied(workspace.copy(pages = without.inserted(moving, edit.toIndex)))
        }
    }

    fun setBinding(
        workspace: Workspace,
        edit: WorkspaceEdit.SetPageBinding,
    ): EditResult {
        val page = workspace.pages.firstOrNull { it.id == edit.pageId }
        val replaced: PageHost? =
            when (page) {
                is PageSetContainer -> page.copy(binding = edit.binding)
                is PageContainer ->
                    if (page.content is PageContent.Bound) {
                        page.copy(
                            content = PageContent.Bound(edit.binding),
                        )
                    } else {
                        null
                    }
                null -> null
            }
        val pages = replaced?.let { new -> workspace.pages.map { if (it.id == edit.pageId) new else it } }
        return when {
            page == null -> EditResult.Rejected(EditRejection.UnknownPage(edit.pageId))
            pages == null -> EditResult.Rejected(EditRejection.NotABoundPage(edit.pageId))
            else -> EditResult.Applied(workspace.copy(pages = pages))
        }
    }
}

package com.riffle.core.domain.launcher.workspace

/** Deep copies with fresh ids. Workspaces hold no item content, so a copy is fully independent. */
internal object WorkspaceCopy {
    fun withFreshIds(
        workspace: Workspace,
        ids: WorkspaceIdFactory,
    ): Workspace =
        workspace.copy(
            id = WorkspaceId(ids.next()),
            pages = workspace.pages.map { page -> withFreshIds(page, ids) },
        )

    private fun withFreshIds(
        page: PageHost,
        ids: WorkspaceIdFactory,
    ): PageHost =
        when (page) {
            is PageSetContainer -> page.copy(id = ContainerId(ids.next()))
            is PageContainer -> page.copy(id = ContainerId(ids.next()), content = withFreshIds(page.content, ids))
        }

    private fun withFreshIds(
        content: PageContent,
        ids: WorkspaceIdFactory,
    ): PageContent =
        when (content) {
            is PageContent.Bound -> content
            is PageContent.WidgetGrid ->
                content.copy(
                    placements =
                        content.placements.map { placement ->
                            placement.copy(widget = placement.widget.copy(id = ContainerId(ids.next())))
                        },
                )
        }
}

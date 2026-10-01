package com.riffle.core.domain.launcher.workspace

internal enum class SiteKind {
    PAGE,
    PAGE_SET,
    WIDGET,
    DOCK,
}

/** Where one [LensBinding] lives. [containerId] is null for the dock's dynamic section. */
internal data class BindingSite(
    val workspaceId: WorkspaceId,
    val containerId: ContainerId?,
    val kind: SiteKind,
    val binding: LensBinding,
) {
    val key: SiteKey get() = SiteKey(workspaceId, containerId)
}

internal data class SiteKey(val workspaceId: WorkspaceId, val containerId: ContainerId?)

/** Walks every binding of a workspace: bound pages, page-sets, grid widgets and the dock section. */
internal object WorkspaceBindings {
    fun sites(workspace: Workspace): List<BindingSite> =
        buildList {
            map(workspace) { site ->
                add(site)
                site.binding
            }
        }

    fun map(
        workspace: Workspace,
        transform: (BindingSite) -> LensBinding,
    ): Workspace {
        fun site(
            id: ContainerId?,
            kind: SiteKind,
            binding: LensBinding,
        ) = BindingSite(workspace.id, id, kind, binding)
        return workspace.copy(
            pages =
                workspace.pages.map { page ->
                    when (page) {
                        is PageSetContainer ->
                            page.copy(binding = transform(site(page.id, SiteKind.PAGE_SET, page.binding)))
                        is PageContainer -> page.copy(content = mapContent(page, ::site, transform))
                    }
                },
            dock =
                workspace.dock.copy(
                    dynamicSection = workspace.dock.dynamicSection?.let { transform(site(null, SiteKind.DOCK, it)) },
                ),
        )
    }

    private fun mapContent(
        page: PageContainer,
        site: (ContainerId?, SiteKind, LensBinding) -> BindingSite,
        transform: (BindingSite) -> LensBinding,
    ): PageContent =
        when (val content = page.content) {
            is PageContent.Bound -> content.copy(binding = transform(site(page.id, SiteKind.PAGE, content.binding)))
            is PageContent.WidgetGrid ->
                content.copy(
                    placements =
                        content.placements.map { placement ->
                            val widget = placement.widget
                            val binding = transform(site(widget.id, SiteKind.WIDGET, widget.binding))
                            placement.copy(widget = widget.copy(binding = binding))
                        },
                )
        }
}

/** Rewrites every binding of every workspace of this layout; the library and ids are untouched. */
internal fun LayoutWorkspaces.mapBindings(transform: (BindingSite) -> LensBinding): LayoutWorkspaces =
    copy(workspaces = workspaces.map { WorkspaceBindings.map(it, transform) })

package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * Workspaces as the build before the cut-over stored them, built by hand now that the presets are gone: the Nova-style
 * default (a home-grid page and a Finder page), and one that also carries lens pages, a saved-lens reference and a
 * dock dynamic section. Tests use them to prove that stored data of that shape still decodes and still shows its
 * placed pages.
 */
internal object TestWorkspaces {
    /** The id the seeded Nova default of the phone layout carried. */
    val NovaId = WorkspaceId("preset:nova:compact")

    fun container(
        workspace: WorkspaceId,
        part: String,
    ) = ContainerId("${workspace.value}:$part")

    /** A home-grid page reading the layout page [pageId], like the presets' home page. */
    fun homePage(
        workspace: WorkspaceId,
        part: String = "home",
        pageId: String = "home",
    ): PageHost =
        PageContainer(
            container(workspace, part),
            PageContent.Bound(LensBinding(homeGridLens(pageId), ExpressionKind.ICON_GRID)),
        )

    fun finderPage(workspace: WorkspaceId): PageHost =
        PageContainer(
            container(workspace, "finder"),
            PageContent.Bound(LensBinding(appsLens(), ExpressionKind.ALPHA_LIST)),
            PageRole.FINDER,
        )

    /** The Nova-style default: the home grid and a Finder page. */
    fun nova(
        id: WorkspaceId = NovaId,
        name: String = "Nova",
    ): Workspace =
        Workspace(
            id = id,
            name = name,
            pages = listOf(homePage(id), finderPage(id)),
            presetId = "nova",
        )

    fun novaFor(deviceClass: HomeLayoutDeviceClass): Workspace =
        nova(WorkspaceId("preset:nova:${if (deviceClass == HomeLayoutDeviceClass.PHONE) "compact" else "expanded"}"))

    /** A workspace whose only page is a lens page (no placed items), like TimeScape. */
    fun lensOnly(id: WorkspaceId = WorkspaceId("lens-only")): Workspace =
        Workspace(
            id = id,
            name = "Lens only",
            pages =
                listOf(
                    PageSetContainer(
                        container(id, "inbox"),
                        LensBinding(notificationsLens().copy(group = LensGroup.ByGroupKey), ExpressionKind.CARD_STACK),
                    ),
                ),
        )

    /**
     * What the owner's builds could store: placed pages, then lens pages (a page-set and a bound page), a Finder
     * page, a binding that names a saved lens ([savedLens], which may or may not exist in the library), and a dock
     * dynamic section.
     */
    fun everything(
        id: WorkspaceId = WorkspaceId("ws:everything"),
        savedLens: LensId = LensId("saved-1"),
    ): Workspace =
        Workspace(
            id = id,
            name = "Everything",
            pages =
                listOf(
                    homePage(id),
                    PageSetContainer(
                        container(id, "inbox"),
                        LensBinding(notificationsLens().copy(group = LensGroup.ByGroupKey), ExpressionKind.CARD_STACK),
                    ),
                    PageContainer(
                        container(id, "saved"),
                        PageContent.Bound(LensBinding(appsLens(), ExpressionKind.ICON_GRID, ref = savedLens)),
                    ),
                    finderPage(id),
                ),
            dock = WorkspaceDock(dynamicSection = LensBinding(notificationsLens(), ExpressionKind.ICON_ROW)),
            presetId = "nova",
            startPageId = container(id, "home"),
        )
}

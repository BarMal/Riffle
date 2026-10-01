package com.riffle.core.domain.launcher.workspace

internal val LL_APPS = SourceId("apps")
internal val LL_NOTES = SourceId("notifications")

internal fun llCounter(prefix: String = "n"): WorkspaceIdFactory {
    var n = 0
    return WorkspaceIdFactory { "$prefix-${n++}" }
}

internal fun llLens(
    source: SourceId = LL_APPS,
    group: LensGroup = LensGroup.None,
    limit: Int? = null,
) = Lens(listOf(source), group = group, limit = limit)

internal fun llBinding(
    expression: ExpressionKind = ExpressionKind.LIST,
    lens: Lens = llLens(),
    ref: LensId? = null,
) = LensBinding(lens, expression, ref)

internal fun llPage(
    id: String,
    binding: LensBinding = llBinding(),
) = PageContainer(ContainerId(id), PageContent.Bound(binding))

internal fun llGrid(
    id: String,
    widgetId: String,
    binding: LensBinding = llBinding(ExpressionKind.ICON_ROW, llLens(limit = 3)),
) = PageContainer(
    ContainerId(id),
    PageContent.WidgetGrid(
        4,
        4,
        listOf(WidgetPlacement(WidgetContainer(ContainerId(widgetId), WidgetSpan(2, 1), binding), 0, 0)),
    ),
)

/** A workspace with every kind of binding site: bound page, page-set, grid widget and dock section. */
internal fun llWorkspace(id: String = "w1") =
    Workspace(
        id = WorkspaceId(id),
        name = id,
        pages =
            listOf(
                llPage("$id-list"),
                PageSetContainer(
                    ContainerId("$id-set"),
                    llBinding(ExpressionKind.CARD_STACK, llLens(LL_NOTES, group = LensGroup.ByGroupKey)),
                ),
                llGrid("$id-grid", "$id-widget"),
            ),
        dock = WorkspaceDock(llBinding(ExpressionKind.ICON_ROW, llLens(limit = 5))),
    )

internal fun llLayout() = LayoutWorkspaces.single(llWorkspace("w1")).add(llWorkspace("w2"))

internal fun LayoutWorkspaces.allBindings(): List<LensBinding> =
    workspaces.flatMap { ws -> WorkspaceBindings.sites(ws).map { it.binding } }

internal fun LayoutWorkspaces.issues(): List<WorkspaceIssue> = workspaces.flatMap { WorkspaceValidation.validate(it) }

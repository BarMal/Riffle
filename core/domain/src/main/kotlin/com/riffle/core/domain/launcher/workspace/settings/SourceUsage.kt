package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BindingSite
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SiteKind
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** What kind of container reads a source. */
enum class SourcePlaceKind {
    PAGE,
    PAGE_SET,
    WIDGET,
    DOCK,
}

/**
 * One place that reads a source: a page, page-set, widget or the dock's dynamic section of one workspace.
 * [pageNumber] is the 1-based position of the page among the workspace's pages (null for the dock);
 * [widgetNumber] is the 1-based position of a widget on its page. [savedLensName] is set when the binding
 * reads through a saved lens that still exists. Names only: never item content.
 */
data class SourcePlace(
    val layout: HomeLayoutDeviceClass,
    val workspaceId: WorkspaceId,
    val workspaceName: String,
    val containerId: ContainerId?,
    val kind: SourcePlaceKind,
    val pageNumber: Int?,
    val widgetNumber: Int?,
    val savedLensName: String?,
)

/** Which places read which source, over every layout given to [SourceUsagePlanner.plan]. */
data class SourceUsage(
    private val bySource: Map<SourceId, List<SourcePlace>> = emptyMap(),
) {
    fun placesOf(id: SourceId): List<SourcePlace> = bySource[id].orEmpty()

    fun countOf(id: SourceId): Int = placesOf(id).size

    /** The layouts that have at least one place, in the order they were planned. */
    fun layoutsOf(id: SourceId): List<HomeLayoutDeviceClass> = placesOf(id).map { it.layout }.distinct()
}

/**
 * Pure: which lens bindings of the given layouts read each source. A binding that references a saved lens that
 * still exists reads what the saved lens reads (the library is the truth); a dangling reference or an inline lens
 * reads its own snapshot. Each binding counts once per source however many times its lens names it. Layouts are
 * listed in [HomeLayoutDeviceClass] order, workspaces and pages in their stored order.
 */
object SourceUsagePlanner {
    fun plan(layouts: Map<HomeLayoutDeviceClass, LayoutWorkspaces>): SourceUsage {
        val collected = LinkedHashMap<SourceId, MutableList<SourcePlace>>()
        layouts.entries.sortedBy { it.key.ordinal }.forEach { (layout, workspaces) ->
            workspaces.workspaces.forEach { workspace -> collect(layout, workspaces, workspace, collected) }
        }
        return SourceUsage(collected)
    }

    private fun collect(
        layout: HomeLayoutDeviceClass,
        workspaces: LayoutWorkspaces,
        workspace: Workspace,
        into: MutableMap<SourceId, MutableList<SourcePlace>>,
    ) {
        val positions = positionsOf(workspace)
        WorkspaceBindings.sites(workspace).forEach { site ->
            val saved = site.binding.ref?.let { workspaces.library.find(it) }
            val lens = saved?.lens ?: site.binding.lens
            val place = placeOf(layout, workspace, site, positions, saved?.name)
            lens.sources.distinct().forEach { id -> into.getOrPut(id) { ArrayList() } += place }
        }
    }

    private class Position(val page: Int, val widget: Int?)

    private fun positionsOf(workspace: Workspace): Map<ContainerId, Position> {
        val result = HashMap<ContainerId, Position>()
        workspace.pages.forEachIndexed { index, page ->
            result[page.id] = Position(index + 1, null)
            val content = (page as? PageContainer)?.content
            if (content is PageContent.WidgetGrid) {
                content.placements.forEachIndexed { i, placement ->
                    result[placement.widget.id] = Position(index + 1, i + 1)
                }
            }
        }
        return result
    }

    private fun placeOf(
        layout: HomeLayoutDeviceClass,
        workspace: Workspace,
        site: BindingSite,
        positions: Map<ContainerId, Position>,
        savedLensName: String?,
    ): SourcePlace {
        val position = site.containerId?.let { positions[it] }
        return SourcePlace(
            layout = layout,
            workspaceId = workspace.id,
            workspaceName = workspace.name,
            containerId = site.containerId,
            kind =
                when (site.kind) {
                    SiteKind.PAGE -> SourcePlaceKind.PAGE
                    SiteKind.PAGE_SET -> SourcePlaceKind.PAGE_SET
                    SiteKind.WIDGET -> SourcePlaceKind.WIDGET
                    SiteKind.DOCK -> SourcePlaceKind.DOCK
                },
            pageNumber = position?.page,
            widgetNumber = position?.widget,
            savedLensName = savedLensName,
        )
    }
}

package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.HomeLayoutWorkspaceMapper
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds

/**
 * Pure read-only view that answers "which placed page does this `home.grid` page show?" for the Workspaces
 * preview. Nothing here edits the pool.
 *
 * A migrated workspace (`ws:<deviceclass>:<mode>`) owns an arrangement of the same id. A preset workspace
 * (`preset:...`) has none yet, so its home page falls back to the migrated arrangement of the layout the user
 * actually has: the shown mode first, then Library, Standard, Cards. Within an arrangement the page named by the
 * page's lens (`GroupKeyIs(<page id>)`) is used, else the first page.
 */
object PoolHomeView {
    /** Whether [page] is a single page bound to placed items, the only kind this view can draw. */
    fun isPlacedHomePage(page: PageHost): Boolean =
        page is PageContainer &&
            (page.content as? PageContent.Bound)?.binding?.lens?.sources?.contains(WorkspaceSourceIds.HOME_GRID) == true

    /** The `HomeLayout` page id a home-grid [page] names, or null when it names none. */
    fun pageKey(page: PageHost): String? =
        ((page as? PageContainer)?.content as? PageContent.Bound)
            ?.binding?.lens?.filter
            ?.let { it as? LensFilter.GroupKeyIs }
            ?.key

    /** Candidate arrangements, best first: the active workspace, then the migrated ones. */
    fun candidates(
        active: WorkspaceId,
        deviceClass: HomeLayoutDeviceClass,
        shownMode: LauncherViewMode,
    ): List<WorkspaceId> =
        (
            listOf(active) +
                (
                    listOf(shownMode) +
                        listOf(
                            LauncherViewMode.HOME_SCREEN_LIBRARY,
                            LauncherViewMode.STANDARD_APP_DRAWER,
                            LauncherViewMode.CARD_INTERFACE,
                        )
                ).map { HomeLayoutWorkspaceMapper.workspaceId(deviceClass, it) }
        ).distinct()

    /** The page to draw for [page], or null when no candidate arrangement has a page (the host shows a notice). */
    fun resolve(
        pool: PlacedItemPool,
        page: PageHost,
        candidates: List<WorkspaceId>,
    ): LauncherPage? =
        resolveTarget(pool, page, candidates)?.let { target ->
            pool.arrangements[target.workspaceId]?.pages?.firstOrNull { it.id == target.pageId }
                ?.let { ArrangementAdapter.toLauncherPage(it, pool) }
        }

    /** The arrangement a page edit changes: the first candidate that has pages. Null when there is none. */
    fun arrangementOf(
        pool: PlacedItemPool,
        candidates: List<WorkspaceId>,
    ): WorkspaceId? = candidates.firstOrNull { pool.arrangements[it]?.pages?.isNotEmpty() == true }

    /**
     * The arrangement and page [resolve] draws, which is also what an edit on that page changes. A preset workspace
     * has no arrangement of its own yet, so its edits change the migrated arrangement it shows (decision in
     * `workspaces-pool-editing.md`).
     */
    fun resolveTarget(
        pool: PlacedItemPool,
        page: PageHost,
        candidates: List<WorkspaceId>,
    ): PoolHomeTarget? {
        val shown =
            candidates.firstNotNullOfOrNull { id ->
                pool.arrangements[id]?.takeIf { it.pages.isNotEmpty() }?.let { id to it }
            }
        val key = pageKey(page)
        val chosen = shown?.second?.pages?.let { pages -> pages.firstOrNull { it.id.value == key } ?: pages.first() }
        return if (shown == null || chosen == null) null else PoolHomeTarget(shown.first, chosen.id)
    }
}

/** The arrangement and page a preview home page shows and edits. */
data class PoolHomeTarget(
    val workspaceId: WorkspaceId,
    val pageId: LauncherPageId,
)

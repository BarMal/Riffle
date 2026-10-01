package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPage
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
    ): LauncherPage? {
        val arrangement =
            candidates.firstNotNullOfOrNull {
                    id ->
                pool.arrangements[id]?.takeIf { it.pages.isNotEmpty() }
            }
        val key = pageKey(page)
        val chosen = arrangement?.pages?.firstOrNull { it.id.value == key } ?: arrangement?.pages?.firstOrNull()
        return chosen?.let { ArrangementAdapter.toLauncherPage(it, pool) }
    }
}

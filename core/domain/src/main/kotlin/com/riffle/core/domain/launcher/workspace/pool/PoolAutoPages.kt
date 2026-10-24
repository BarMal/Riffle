package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/**
 * "Pages appear as you fill them" (design 9.8, `autoPages`). Arrangement-only: items are never added, removed or
 * moved. Callers decide whether the workspace has `autoPages` on (`NewAppDefaults.resolve`).
 */
object PoolAutoPages {
    /**
     * Appends one empty page to [workspaceId]'s arrangement, with the grid of its last page and a fresh page id.
     * Null when the workspace has no arrangement or no page to take a grid from.
     */
    fun appendPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        ids: WorkspaceIdFactory,
    ): Pair<PlacedItemPool, LauncherPageId>? {
        val arrangement = pool.arrangements[workspaceId]
        val last = arrangement?.pages?.lastOrNull()
        return if (arrangement == null || last == null) {
            null
        } else {
            val taken = arrangement.pages.map { it.id }.toSet()
            var id = LauncherPageId("$PAGE_ID_PREFIX${ids.next()}")
            while (id in taken) id = LauncherPageId("$PAGE_ID_PREFIX${ids.next()}")
            val next = arrangement.copy(pages = arrangement.pages + ArrangementPage(id = id, grid = last.grid))
            pool.copy(arrangements = pool.arrangements + (workspaceId to next)) to id
        }
    }

    /**
     * On leaving edit mode: removes empty, unpinned pages from the end of [workspaceId]'s arrangement, never the
     * first page. Unchanged when there is nothing to trim.
     */
    fun trimTrailingEmpty(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
    ): PlacedItemPool {
        val arrangement = pool.arrangements[workspaceId] ?: return pool
        val pages = arrangement.pages.toMutableList()
        while (pages.size > 1 && pages.last().let { it.placements.isEmpty() && !it.isPinned }) {
            pages.removeAt(pages.lastIndex)
        }
        return if (pages.size == arrangement.pages.size) {
            pool
        } else {
            pool.copy(arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = pages)))
        }
    }

    private const val PAGE_ID_PREFIX = "page-"
}

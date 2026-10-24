package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolArrangements
import com.riffle.core.domain.launcher.workspace.pool.PoolGc
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeEditing
import com.riffle.core.domain.launcher.workspace.pool.PoolRejection
import com.riffle.core.domain.launcher.workspace.pool.PoolResult

/** The default home grid for an arrangement the wizard creates (the standard home's phone grid). */
val DEFAULT_HOME_GRID: GridDimensions = GridDimensions(columns = 4, rows = 5)

/** What [PlacedApps.place] did. [pageKey] is the arrangement page the apps went to (the `home.grid` lens names it). */
data class PlacedAppsResult(
    val pool: PlacedItemPool,
    val pageKey: String?,
    /** Apps now shown by the workspace (placed now, or already shown there). */
    val placed: List<AppPick>,
    /** Apps that found no room. */
    val skipped: List<AppPick>,
    /** Whether the workspace got its own arrangement from the one it borrowed (fork on first edit). */
    val forked: Boolean = false,
)

/**
 * Puts picked apps on a placed page of a workspace through the existing pool operations
 * ([PoolArrangements.fork], [PoolHomeEditing.addApp]; a missing page is appended the way
 * [PoolHomeEditing.addPage] does, but under a caller-chosen id). Pure.
 *
 * The workspace's arrangement: its own when it has one; else, with [keepHome], a fork of the one it borrows
 * (`candidates`, as `PoolHomeView.candidates` gives them) so the user's current home stays and gains a page; else (or
 * when there is nothing to fork) a new arrangement holding only page [pageId]. The apps go on page [pageId] (created
 * with the arrangement's grid when absent, [grid] for a new arrangement), spilling to the next page with room as
 * `addApp` does. An app the workspace already shows is counted as placed and not added twice, so applying the same
 * picks again changes nothing. With no apps the pool is untouched (a borrowing workspace keeps borrowing).
 */
object PlacedApps {
    @Suppress("LongParameterList") // One pool operation over its explicit inputs; each has a meaning of its own.
    fun place(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        apps: List<AppPick>,
        pageId: LauncherPageId,
        keepHome: Boolean,
        candidates: List<WorkspaceId>,
        ids: WorkspaceIdFactory,
        grid: GridDimensions = DEFAULT_HOME_GRID,
    ): PlacedAppsResult {
        if (apps.isEmpty()) return PlacedAppsResult(pool, null, emptyList(), emptyList())
        val (owned, forked) = ownArrangement(pool, workspaceId, pageId, keepHome, candidates, ids, grid)
        var working = withPage(owned, workspaceId, pageId, grid)
        val placed = mutableListOf<AppPick>()
        val skipped = mutableListOf<AppPick>()
        apps.distinctBy { it.identity }.forEach { app ->
            when (
                val result = PoolHomeEditing.addApp(working, workspaceId, pageId, app.identity, app.label, ids)
            ) {
                is PoolResult.Done -> {
                    working = result.edit.pool
                    placed += app
                }
                is PoolResult.Rejected ->
                    if (result.reason == PoolRejection.ALREADY_IN_ARRANGEMENT) placed += app else skipped += app
            }
        }
        return PlacedAppsResult(PoolGc.finish(pool, working).pool, pageId.value, placed, skipped, forked)
    }

    private fun ownArrangement(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        keepHome: Boolean,
        candidates: List<WorkspaceId>,
        ids: WorkspaceIdFactory,
        grid: GridDimensions,
    ): Pair<PlacedItemPool, Boolean> {
        if (PoolArrangements.ownsArrangement(pool, workspaceId)) return pool to false
        val fork = if (keepHome) PoolArrangements.fork(pool, workspaceId, candidates, ids) as? PoolResult.Done else null
        return if (fork != null) {
            fork.edit.pool to true
        } else {
            val fresh = Arrangement(pages = listOf(ArrangementPage(id = pageId, grid = grid)))
            pool.copy(arrangements = pool.arrangements + (workspaceId to fresh)) to false
        }
    }

    private fun withPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        grid: GridDimensions,
    ): PlacedItemPool {
        val arrangement = pool.arrangements[workspaceId]?.takeIf { owned -> owned.pages.none { it.id == pageId } }
        if (arrangement == null) return pool
        val page = ArrangementPage(id = pageId, grid = arrangement.pages.lastOrNull()?.grid ?: grid)
        return pool.copy(
            arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = arrangement.pages + page)),
        )
    }
}

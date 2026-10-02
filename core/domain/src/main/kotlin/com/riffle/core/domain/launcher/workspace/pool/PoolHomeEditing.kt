package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/** Where an item sits in one arrangement. */
data class PoolItemSite(
    val page: ArrangementPage,
    val pageIndex: Int,
    val placement: Placement,
)

/** What "Delete everywhere" would remove, for the confirmation text. */
data class EverywhereImpact(
    /** Total references to the item across every arrangement. */
    val references: Int,
    /** Workspaces other than the edited one that also show it. */
    val otherWorkspaces: List<WorkspaceId>,
)

/**
 * The edit operations the preview's home page offers, built on the merged pure pool operations. Each returns a
 * [PoolResult] (the new pool as one atomic [PoolEdit], or a typed refusal with nothing changed). Geometry is always
 * `GridPlacementEngine`'s, reached through [PoolPlacement] and the arrangement adapter; nothing here re-implements
 * bounds, collision or first-free-cell logic.
 *
 * Every move has a button-friendly form (cell nudges, previous/next page) so no edit is drag-only.
 */
object PoolHomeEditing {
    fun siteOf(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
    ): PoolItemSite? {
        val pages = pool.arrangements[workspaceId]?.pages.orEmpty()
        return pages.withIndex().firstNotNullOfOrNull { (index, page) ->
            page.placements.firstOrNull { it.item == itemId }?.let { PoolItemSite(page, index, it) }
        }
    }

    /** Moves [itemId] to [cell] on [toPage], keeping its span. */
    fun moveToCell(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
        toPage: LauncherPageId,
        cell: GridCell,
    ): PoolResult {
        val site = siteOf(pool, workspaceId, itemId) ?: return rejected(PoolRejection.NOT_PLACED_HERE)
        return PoolPlacement.move(pool, workspaceId, itemId, toPage, GridPlacement(cell, site.placement.at.span))
    }

    /** Moves [itemId] by whole cells on its own page (the button form of a drag). */
    fun nudge(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
        columns: Int,
        rows: Int,
    ): PoolResult {
        val site = siteOf(pool, workspaceId, itemId) ?: return rejected(PoolRejection.NOT_PLACED_HERE)
        val from = site.placement.at.cell
        return moveToCell(pool, workspaceId, itemId, site.page.id, GridCell(from.column + columns, from.row + rows))
    }

    /**
     * Moves [itemId] to the page [delta] pages away: to the same cell when it is free there, else to the first cell
     * (row by row) the engine accepts. Refused when there is no such page or the page has no room.
     */
    fun moveToAdjacentPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
        delta: Int,
    ): PoolResult {
        val site = siteOf(pool, workspaceId, itemId) ?: return rejected(PoolRejection.NOT_PLACED_HERE)
        val target = pool.arrangements[workspaceId]?.pages?.getOrNull(site.pageIndex + delta)
        return if (target == null) {
            rejected(PoolRejection.UNKNOWN_PAGE)
        } else {
            val home = site.placement.at.cell
            val scan =
                (0 until target.grid.rows).flatMap { row -> (0 until target.grid.columns).map { GridCell(it, row) } }
            var last: PoolResult = rejected(PoolRejection.NO_AVAILABLE_CELL)
            for (cell in listOf(home) + scan) {
                last = moveToCell(pool, workspaceId, itemId, target.id, cell)
                if (last is PoolResult.Done) break
            }
            if (last is PoolResult.Done) last else rejected(PoolRejection.NO_AVAILABLE_CELL)
        }
    }

    /**
     * Adds an app to [workspaceId]'s arrangement at the first free cell, trying [preferredPage] first, then the other
     * pages in order. An app the pool already holds is reused (apps are shared freely); otherwise one new [PoolApp] is
     * created. Refused when the arrangement already shows this app or no page has room.
     */
    fun addApp(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        preferredPage: LauncherPageId?,
        identity: AppIdentity,
        label: String,
        ids: WorkspaceIdFactory,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId] ?: return rejected(PoolRejection.UNKNOWN_WORKSPACE)
        val shown = PoolReferences.referencedIn(arrangement)
        val existing =
            pool.items.values.firstOrNull { it is PoolApp && it.appIdentity == identity && it.appShortcutId == null }
        val ordered = arrangement.pages.sortedBy { if (it.id == preferredPage) 0 else 1 }
        return when {
            ordered.isEmpty() -> rejected(PoolRejection.UNKNOWN_PAGE)
            existing != null && existing.id in shown -> rejected(PoolRejection.ALREADY_IN_ARRANGEMENT)
            else -> {
                val app = existing ?: PoolApp(PoolIds.fresh(pool.items.keys, ids), identity, label)
                var last: PoolResult = rejected(PoolRejection.NO_AVAILABLE_CELL)
                for (page in ordered) {
                    last =
                        if (existing != null) {
                            PoolPlacement.placeExisting(pool, workspaceId, page.id, app.id)
                        } else {
                            PoolPlacement.addNew(pool, workspaceId, page.id, app)
                        }
                    if (last is PoolResult.Done) break
                }
                last
            }
        }
    }

    /** Appends an empty page with the grid of the last page. */
    fun addPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        ids: WorkspaceIdFactory,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId]
        val last = arrangement?.pages?.lastOrNull()
        return if (arrangement == null) {
            rejected(PoolRejection.UNKNOWN_WORKSPACE)
        } else if (last == null) {
            rejected(PoolRejection.UNKNOWN_PAGE)
        } else {
            var id = LauncherPageId(PAGE_PREFIX + ids.next())
            while (arrangement.pages.any { it.id == id }) id = LauncherPageId(PAGE_PREFIX + ids.next())
            val page = ArrangementPage(id = id, grid = last.grid)
            PoolResult.Done(
                PoolEdit(
                    pool.copy(
                        arrangements =
                            pool.arrangements + (workspaceId to arrangement.copy(pages = arrangement.pages + page)),
                    ),
                ),
            )
        }
    }

    /** Removes an empty page; the last page of an arrangement and a page that still holds items are refused. */
    fun removeEmptyPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId]
        val page = arrangement?.pages?.firstOrNull { it.id == pageId }
        return when {
            arrangement == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            page == null -> rejected(PoolRejection.UNKNOWN_PAGE)
            page.placements.isNotEmpty() -> rejected(PoolRejection.PAGE_NOT_EMPTY)
            arrangement.pages.size == 1 -> rejected(PoolRejection.LAST_PAGE)
            else -> {
                val remaining = arrangement.pages.filter { it.id != pageId }
                PoolResult.Done(
                    PoolEdit(
                        pool.copy(
                            arrangements =
                                pool.arrangements + (workspaceId to arrangement.copy(pages = remaining)),
                        ),
                    ),
                )
            }
        }
    }

    /** What deleting [itemId] everywhere would affect: its references, and the other workspaces that show it. */
    fun everywhereImpact(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
    ): EverywhereImpact {
        val sites = PoolReferences.index(pool)[itemId].orEmpty()
        return EverywhereImpact(sites.size, sites.map { it.workspaceId }.distinct().filter { it != workspaceId })
    }

    private const val PAGE_PREFIX = "page-"
}

/** Host ids the standard home (`HomeLayoutSet`) holds, which pool editing must never delete. */
object PoolHostIds {
    /** Every bound widget host id in every stored layout of [layoutSet]. */
    fun standardHome(layoutSet: HomeLayoutSet): Set<HostedWidgetId> =
        layoutSet.layouts.values
            .flatMap { it.pages }
            .flatMap { it.items }
            .filterIsInstance<WidgetItem>()
            .map { it.appWidgetId }
            .filter { it != ArrangementAdapter.UNBOUND_HOST_ID }
            .toSet()

    /**
     * The released ids that are safe to delete: not held by the standard home. The preview's migrated widgets share
     * a host id with the standard home's widget (it is only read, never changed), so removing one in the preview must
     * not delete the platform id the standard home still draws.
     */
    fun deletable(
        released: Set<HostedWidgetId>,
        standardHome: Set<HostedWidgetId>,
    ): Set<HostedWidgetId> = released - standardHome
}

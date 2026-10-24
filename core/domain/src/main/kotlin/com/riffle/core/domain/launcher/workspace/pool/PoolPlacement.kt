package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/**
 * Place and move references. Pure: each returns a [PoolResult] holding the new pool, or the typed reason it was
 * refused with nothing changed. Geometry (bounds, collisions, first free cell) is `GridPlacementEngine`'s.
 */
object PoolPlacement {
    /** Adds a NEW [item] to the pool and places it; at [at], or in the first free cell for [span] when null. */
    fun addNew(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        item: PoolItem,
        at: GridPlacement? = null,
        span: GridSpan = GridSpan(),
    ): PoolResult {
        val hostId = (item as? PoolWidget)?.hostedId
        return when {
            item.id in pool.items -> rejected(PoolRejection.ITEM_ID_IN_USE)
            hostId != null && hostId in PoolReferences.hostIds(pool) -> rejected(PoolRejection.HOST_ID_IN_USE)
            else ->
                PoolPages.place(
                    pool.copy(items = pool.items + (item.id to item)),
                    workspaceId,
                    pageId,
                    item.id,
                    at,
                    span,
                )
                    .rebased(pool)
        }
    }

    /**
     * Places an existing app, shortcut or widget in another arrangement (or on another page of one that does not show
     * it yet): apps and widget instances are shared freely, once per arrangement. A widget keeps its one host id and
     * configuration wherever it is placed; a divergent configuration is [PoolWidgets.separateCopy]. A folder is refused
     * (sharing is [shareFolder]); a dock item is refused ([PoolRejection.ITEM_IN_DOCK]).
     */
    fun placeExisting(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        itemId: PoolItemId,
        at: GridPlacement? = null,
    ): PoolResult =
        when (pool.items[itemId]) {
            is PoolFolder -> rejected(PoolRejection.FOLDER_SHARING_IS_EXPLICIT)
            else -> PoolPages.place(pool, workspaceId, pageId, itemId, at, GridSpan())
        }

    /** The explicit "Also show in..." action for a folder: one folder, placed in several workspaces. */
    fun shareFolder(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        folderId: PoolItemId,
        at: GridPlacement? = null,
    ): PoolResult =
        when (pool.items[folderId]) {
            is PoolFolder -> PoolPages.place(pool, workspaceId, pageId, folderId, at, GridSpan())
            null -> rejected(PoolRejection.UNKNOWN_ITEM)
            else -> rejected(PoolRejection.NOT_A_FOLDER)
        }

    /** Moves an item placed in [workspaceId] to [to] on [toPage] (any page of the arrangement). */
    fun move(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
        toPage: LauncherPageId,
        to: GridPlacement,
    ): PoolResult {
        val source =
            pool.arrangements[workspaceId]?.pages?.firstOrNull {
                    page ->
                page.placements.any { it.item == itemId }
            }
        val target = pool.arrangements[workspaceId]?.pages?.firstOrNull { it.id == toPage }
        val item = pool.items[itemId]
        return when {
            pool.arrangements[workspaceId] == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            target == null -> rejected(PoolRejection.UNKNOWN_PAGE)
            item == null -> rejected(PoolRejection.UNKNOWN_ITEM)
            source == null -> rejected(PoolRejection.NOT_PLACED_HERE)
            !PoolPages.permits(item, to.span) -> rejected(PoolRejection.INVALID_PLACEMENT)
            source.id == target.id -> moveWithinPage(pool, workspaceId, target, itemId, to)
            else ->
                PoolPages.dropPlacement(pool, workspaceId, itemId)
                    .let { dropped -> PoolPages.place(dropped, workspaceId, toPage, itemId, to, to.span) }
                    .rebased(pool)
        }
    }

    /**
     * Moves an item's placement from one workspace to another: the source loses its reference, the host id of a widget
     * is kept. Refused when the target already shows the item.
     */
    fun moveToWorkspace(
        pool: PlacedItemPool,
        itemId: PoolItemId,
        from: WorkspaceId,
        to: WorkspaceId,
        toPage: LauncherPageId,
        at: GridPlacement? = null,
    ): PoolResult {
        val placedInFrom = pool.arrangements[from]?.let { itemId in PoolReferences.referencedIn(it) } == true
        return when {
            from == to -> rejected(PoolRejection.SAME_WORKSPACE)
            pool.arrangements[from] == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            !placedInFrom -> rejected(PoolRejection.NOT_PLACED_HERE)
            else ->
                PoolPages.place(
                    PoolPages.dropPlacement(pool, from, itemId),
                    to,
                    toPage,
                    itemId,
                    at,
                    at?.span ?: GridSpan(),
                )
                    .rebased(pool)
        }
    }

    private fun moveWithinPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        page: ArrangementPage,
        itemId: PoolItemId,
        to: GridPlacement,
    ): PoolResult =
        when (
            val result =
                GridPlacementEngine().moveItem(
                    ArrangementAdapter.toLauncherPage(page, pool),
                    LauncherItemId(itemId.value),
                    to,
                )
        ) {
            is PlaceLauncherItemResult.Placed ->
                PoolResult.Done(
                    PoolGc.finish(
                        pool,
                        PoolPages.withPage(
                            pool,
                            workspaceId,
                            page.copy(placements = ArrangementAdapter.placementsOf(result.page)),
                        ),
                    ),
                )
            is PlaceLauncherItemResult.Rejected -> rejected(result.reason.toPoolRejection())
        }
}

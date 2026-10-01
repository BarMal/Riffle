package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** Shared page-level steps of the placement operations. */
internal object PoolPages {
    private val engine = GridPlacementEngine()

    /** Places [itemId] (already in [pool.items]) on a page, enforcing the pool rules, then the engine's. */
    fun place(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        itemId: PoolItemId,
        at: GridPlacement?,
        span: GridSpan,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId]
        val page = arrangement?.pages?.firstOrNull { it.id == pageId }
        val item = pool.items[itemId]
        return when {
            arrangement == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            page == null -> rejected(PoolRejection.UNKNOWN_PAGE)
            item == null -> rejected(PoolRejection.UNKNOWN_ITEM)
            item is PoolWidget && PoolReferences.count(pool, itemId) > 0 ->
                rejected(
                    PoolRejection.WIDGET_ALREADY_PLACED,
                )
            itemId in PoolReferences.referencedIn(arrangement) -> rejected(PoolRejection.ALREADY_IN_ARRANGEMENT)
            !permits(item, at?.span ?: span) -> rejected(PoolRejection.INVALID_PLACEMENT)
            else -> engineStep(pool, workspaceId, page, item, at, span)
        }
    }

    /** A widget span must satisfy the provider's resize constraints; every other item takes any positive span. */
    fun permits(
        item: PoolItem,
        span: GridSpan,
    ): Boolean = span.columns > 0 && span.rows > 0 && (item as? PoolWidget)?.resizeConstraints?.permits(span) != false

    fun withPage(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        page: ArrangementPage,
    ): PlacedItemPool =
        pool.arrangements[workspaceId]?.let { arrangement ->
            val pages = arrangement.pages.map { if (it.id == page.id) page else it }
            pool.copy(arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = pages)))
        } ?: pool

    /** [pool] without [itemId]'s placements in [workspaceId]; the item itself stays until collected. */
    fun dropPlacement(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
    ): PlacedItemPool =
        pool.arrangements[workspaceId]?.let { arrangement ->
            val pages =
                arrangement.pages.map {
                        page ->
                    page.copy(placements = page.placements.filter { it.item != itemId })
                }
            pool.copy(arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = pages)))
        } ?: pool

    private fun engineStep(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        page: ArrangementPage,
        item: PoolItem,
        at: GridPlacement?,
        span: GridSpan,
    ): PoolResult {
        val launcherPage = ArrangementAdapter.toLauncherPage(page, pool)
        val result =
            if (at == null) {
                engine.placeItemInFirstAvailableCell(launcherPage, ArrangementAdapter.toItem(item, null), span)
            } else {
                engine.placeItem(launcherPage, ArrangementAdapter.toItem(item, at))
            }
        return when (result) {
            is PlaceLauncherItemResult.Placed ->
                PoolResult.Done(
                    PoolGc.finish(
                        pool,
                        withPage(
                            pool,
                            workspaceId,
                            page.copy(placements = ArrangementAdapter.placementsOf(result.page)),
                        ),
                    ),
                )
            is PlaceLauncherItemResult.Rejected -> rejected(result.reason.toPoolRejection())
        }
    }
}

/** Re-describes a successful result as the change from [original] (collecting what became unreferenced). */
internal fun PoolResult.rebased(original: PlacedItemPool): PoolResult =
    when (this) {
        is PoolResult.Done -> PoolResult.Done(PoolGc.finish(original, edit.pool))
        is PoolResult.Rejected -> this
    }

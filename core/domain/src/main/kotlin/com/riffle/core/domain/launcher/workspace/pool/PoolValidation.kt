package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.workspace.WorkspaceId

enum class PoolIssueKind {
    DANGLING_REFERENCE,
    DUPLICATE_IN_ARRANGEMENT,
    WIDGET_MULTIPLY_PLACED,
    DUPLICATE_HOST_ID,
    INVALID_GEOMETRY,
    DUPLICATE_PAGE,
    ORPHAN_ITEM,
    UNPLACED_ITEM,
}

/** Something [PoolValidation.repair] had to drop. Carries ids only, never labels. */
data class PoolIssue(
    val kind: PoolIssueKind,
    val workspaceId: WorkspaceId? = null,
    val pageId: LauncherPageId? = null,
    val itemId: PoolItemId? = null,
)

data class PoolRepair(
    val pool: PlacedItemPool,
    val issues: List<PoolIssue>,
) {
    val isClean: Boolean get() = issues.isEmpty()
}

/**
 * Re-establishes the pool invariants on data from outside (decode, restore, migration):
 * 1. every placement resolves, 2. an item is placed at most once per arrangement, 3. a widget is placed at most
 * once in the layout and a host id is held by one widget, plus: a placement lies in its grid and does not
 * collide (the `GridPlacementEngine` rules), and no item is left without a reference. The first occurrence in
 * workspace, page and placement order wins. Idempotent; never throws.
 */
object PoolValidation {
    fun repair(pool: PlacedItemPool): PoolRepair {
        val run = RepairRun(pool.items)
        val arrangements = pool.arrangements.mapValues { (id, arrangement) -> run.arrangement(id, arrangement) }
        val repaired = PoolGc.collect(pool.copy(arrangements = arrangements))
        val orphans = pool.items.keys.filter { it !in repaired.items }
        return PoolRepair(repaired, run.issues + orphans.map { PoolIssue(PoolIssueKind.ORPHAN_ITEM, itemId = it) })
    }

    /** Drops arrangements of workspaces not in [workspaceIds] and collects what only they referenced. */
    fun retainWorkspaces(
        pool: PlacedItemPool,
        workspaceIds: Set<WorkspaceId>,
    ): PlacedItemPool = PoolGc.collect(pool.copy(arrangements = pool.arrangements.filterKeys { it in workspaceIds }))

    private class RepairRun(private val items: Map<PoolItemId, PoolItem>) {
        val issues = mutableListOf<PoolIssue>()
        private val engine = GridPlacementEngine()
        private val widgets = HashSet<PoolItemId>()
        private val hostIds = HashSet<HostedWidgetId>()

        fun arrangement(
            workspaceId: WorkspaceId,
            arrangement: Arrangement,
        ): Arrangement {
            val inArrangement = HashSet<PoolItemId>()
            val pageIds = HashSet<LauncherPageId>()
            val pages =
                arrangement.pages.mapNotNull { page ->
                    if (pageIds.add(page.id)) {
                        page(workspaceId, page, inArrangement)
                    } else {
                        issues += PoolIssue(PoolIssueKind.DUPLICATE_PAGE, workspaceId, page.id)
                        null
                    }
                }
            return arrangement.copy(pages = pages)
        }

        private fun page(
            workspaceId: WorkspaceId,
            page: ArrangementPage,
            inArrangement: MutableSet<PoolItemId>,
        ): ArrangementPage {
            var launcher = LauncherPage(id = page.id, grid = page.grid)
            val kept = ArrayList<Placement>()
            page.placements.forEach { placement ->
                val item = items[placement.item]
                val problem =
                    if (item == null) PoolIssueKind.DANGLING_REFERENCE else problem(item, inArrangement)
                val placed =
                    if (problem == null && item != null) {
                        engine.placeItem(
                            launcher,
                            ArrangementAdapter.toItem(item, placement.at),
                        )
                    } else {
                        null
                    }
                if (placed is PlaceLauncherItemResult.Placed) {
                    launcher = placed.page
                    kept += placement
                    accept(placement.item, item)
                    inArrangement += placement.item
                } else {
                    val kind = problem ?: PoolIssueKind.INVALID_GEOMETRY
                    issues += PoolIssue(kind, workspaceId, page.id, placement.item)
                }
            }
            return page.copy(placements = kept)
        }

        private fun problem(
            item: PoolItem,
            inArrangement: Set<PoolItemId>,
        ): PoolIssueKind? =
            when {
                item.id in inArrangement -> PoolIssueKind.DUPLICATE_IN_ARRANGEMENT
                item is PoolWidget && item.id in widgets -> PoolIssueKind.WIDGET_MULTIPLY_PLACED
                item is PoolWidget && item.hostedId?.let { it in hostIds } == true -> PoolIssueKind.DUPLICATE_HOST_ID
                else -> null
            }

        private fun accept(
            id: PoolItemId,
            item: PoolItem?,
        ) {
            if (item is PoolWidget) {
                widgets += id
                item.hostedId?.let { hostIds += it }
            }
        }
    }
}

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

    /** No longer reported: a widget may be placed in any number of arrangements (once per arrangement). */
    WIDGET_MULTIPLY_PLACED,
    DUPLICATE_HOST_ID,
    INVALID_GEOMETRY,
    DUPLICATE_PAGE,
    ORPHAN_ITEM,
    UNPLACED_ITEM,

    /** An item referenced twice by the dock (pins and panel together). */
    DUPLICATE_IN_DOCK,

    /** An arrangement placement of an item the dock holds (dock items are exclusive to the dock). */
    DOCK_ITEM_IN_ARRANGEMENT,
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
 * 1. every reference resolves, 2. an item is referenced at most once per arrangement and at most once by the dock,
 * 3. a host id is held by one pool widget (the same widget may sit in any number of arrangements), 4. an item the dock
 * references is referenced by no arrangement, plus: a placement lies in its grid and does not collide (the
 * `GridPlacementEngine` rules), and no item is left without a reference. The dock is repaired first (it is drawn beside
 * every workspace), then the first occurrence in workspace, page and placement order wins. Idempotent; never throws.
 */
object PoolValidation {
    fun repair(pool: PlacedItemPool): PoolRepair {
        val run = RepairRun(pool.items)
        val dock = pool.dock?.let(run::dock)
        val arrangements = pool.arrangements.mapValues { (id, arrangement) -> run.arrangement(id, arrangement) }
        val repaired = PoolGc.collect(pool.copy(arrangements = arrangements, dock = dock))
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
        private val hostOwners = HashMap<HostedWidgetId, PoolItemId>()
        private val inDock = HashSet<PoolItemId>()

        fun dock(dock: PoolDock): PoolDock {
            val pins =
                dock.pins.filter { id ->
                    val kind =
                        when {
                            items[id] == null -> PoolIssueKind.DANGLING_REFERENCE
                            id in inDock -> PoolIssueKind.DUPLICATE_IN_DOCK
                            !ownsHostId(items.getValue(id)) -> PoolIssueKind.DUPLICATE_HOST_ID
                            else -> null
                        }
                    if (kind == null) {
                        inDock += id
                        claimHostId(items.getValue(id))
                    } else {
                        issues += PoolIssue(kind, itemId = id)
                    }
                    kind == null
                }
            val panel = dock.panel?.let { page(null, it, inDock, inDock) }
            return dock.copy(pins = pins, panel = panel)
        }

        fun arrangement(
            workspaceId: WorkspaceId,
            arrangement: Arrangement,
        ): Arrangement {
            val inArrangement = HashSet<PoolItemId>()
            val pageIds = HashSet<LauncherPageId>()
            val pages =
                arrangement.pages.mapNotNull { page ->
                    if (pageIds.add(page.id)) {
                        page(workspaceId, page, inArrangement, inDock)
                    } else {
                        issues += PoolIssue(PoolIssueKind.DUPLICATE_PAGE, workspaceId, page.id)
                        null
                    }
                }
            return arrangement.copy(pages = pages)
        }

        /** [seen] collects what this page set references; [dockItems] are refused (empty set for the panel itself). */
        private fun page(
            workspaceId: WorkspaceId?,
            page: ArrangementPage,
            seen: MutableSet<PoolItemId>,
            dockItems: Set<PoolItemId>,
        ): ArrangementPage {
            val isPanel = workspaceId == null
            var launcher = LauncherPage(id = page.id, grid = page.grid)
            val kept = ArrayList<Placement>()
            page.placements.forEach { placement ->
                val item = items[placement.item]
                val problem =
                    when {
                        item == null -> PoolIssueKind.DANGLING_REFERENCE
                        placement.item in seen && isPanel -> PoolIssueKind.DUPLICATE_IN_DOCK
                        placement.item in seen -> PoolIssueKind.DUPLICATE_IN_ARRANGEMENT
                        !isPanel && placement.item in dockItems -> PoolIssueKind.DOCK_ITEM_IN_ARRANGEMENT
                        !ownsHostId(item) -> PoolIssueKind.DUPLICATE_HOST_ID
                        else -> null
                    }
                val placed =
                    if (problem == null && item != null) {
                        engine.placeItem(launcher, ArrangementAdapter.toItem(item, placement.at))
                    } else {
                        null
                    }
                if (placed is PlaceLauncherItemResult.Placed) {
                    launcher = placed.page
                    kept += placement
                    seen += placement.item
                    item?.let(::claimHostId)
                } else {
                    val kind = problem ?: PoolIssueKind.INVALID_GEOMETRY
                    issues += PoolIssue(kind, workspaceId, page.id, placement.item)
                }
            }
            return page.copy(placements = kept)
        }

        /** Whether [item] may hold its host id: it is not a widget, is unbound, or no OTHER widget holds the id. */
        private fun ownsHostId(item: PoolItem): Boolean {
            val hostId = (item as? PoolWidget)?.hostedId ?: return true
            val owner = hostOwners[hostId]
            return owner == null || owner == item.id
        }

        private fun claimHostId(item: PoolItem) {
            (item as? PoolWidget)?.hostedId?.let { hostOwners.putIfAbsent(it, item.id) }
        }
    }
}

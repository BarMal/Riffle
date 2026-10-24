package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/**
 * The standard home rendered and edited FROM the pool (S4 cutover). [project] presents one workspace's arrangement and
 * the layout's dock as the `HomeLayout` the existing engines and the standard home surface already use; [ingest] maps
 * an engine-edited `HomeLayout` back into the pool. Together they let `HomeLayoutSet` stop holding placed items and the
 * dock: it keeps only what is genuinely per mode (generated and All-apps pages, settings, template, selection).
 *
 * Id rule: a projected `LauncherItemId` IS the `PoolItemId` text (no prefix, no stripping), so an edit round-trips
 * without re-minting. [ingest] trusts an id only if `before` showed it and the pool holds it in this arrangement or the
 * dock; every other id (an engine-minted one, which may collide with an id another arrangement uses) gets a fresh pool
 * id from the factory.
 */
object PoolHomeProjection {
    /**
     * [base] with its `Home` pages replaced by [workspaceId]'s arrangement and its dock by the pool's dock. Generated
     * and All-apps pages, settings, template, view mode, edit mode and the selected page come from [base]: the k-th
     * `Home` page of [base] is replaced by the k-th arrangement page, extra arrangement pages follow the last `Home`
     * page of [base] (or lead when [base] has none), and `Home` slots beyond the arrangement are dropped. The selected
     * page is kept when it still exists, else the first `Home` page. A pool without a dock (not imported yet) keeps
     * [base]'s dock. A workspace without an arrangement projects no `Home` pages; if nothing would be left, one empty
     * `Home` page with [base]'s first home page id (or `home`) and the settings grid stands in, so the result is a
     * valid layout.
     */
    fun project(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        base: HomeLayout,
    ): HomeLayout {
        val home = pool.arrangements[workspaceId]?.let { ArrangementAdapter.toLauncherPages(it, pool) }.orEmpty()
        val pages = mergeHomePages(base.pages, home).ifEmpty { listOf(standIn(base)) }
        val selected =
            base.selectedPageId.takeIf { id -> pages.any { it.id == id } }
                ?: (pages.firstOrNull { it.type == LauncherPageType.Home } ?: pages.first()).id
        return base.copy(pages = pages, selectedPageId = selected, dock = projectDock(pool) ?: base.dock)
    }

    /** The pool's dock as a `DockModel` (pins and panel joined with their items), or null when it has none yet. */
    fun projectDock(pool: PlacedItemPool): DockModel? =
        pool.dock?.let { dock ->
            dock.settings.toDockModel(
                items = dock.pins.mapNotNull { id -> pool.items[id]?.let { ArrangementAdapter.toItem(it, null) } },
                panel = dock.panel?.let { ArrangementAdapter.toLauncherPage(it, pool) },
            )
        }

    /**
     * Maps an engine-edited [after] (made from [before], which [project] produced for [workspaceId]) back into the
     * pool: `Home` pages become [workspaceId]'s arrangement pages (order, ids, grids, pinned flags; non-`Home` pages
     * are not the pool's and are ignored), the dock's pins, panel and settings become the pool's dock (Library's edge,
     * which `DockModel` does not carry, is kept). Placements that moved are placement edits; changed folder content,
     * labels and widget constraints edit the single pool item (visible wherever it is placed); ids new in [after]
     * become new pool items with fresh ids; vanished ids lose their reference here and are collected only when nothing
     * else references them, so a widget another workspace still shows keeps its host id.
     *
     * Dock items stay exclusive to the dock: an item that enters the dock while another arrangement still shows it is
     * cloned for the dock when it is an app or folder; a widget instance moves into the dock and leaves the other
     * arrangements (it would otherwise be on screen twice). A workspace without an arrangement gets one. The result
     * goes through [PoolValidation.repair] and [PoolGc.finish], so [PoolEdit.releasedHostIds] are exactly the host ids
     * that no arrangement and no dock references any more (to delete after the Undo window, never at once).
     */
    fun ingest(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        before: HomeLayout,
        after: HomeLayout,
        ids: WorkspaceIdFactory,
    ): PoolEdit {
        val arrangement = pool.arrangements[workspaceId] ?: Arrangement()
        val held = (PoolReferences.referencedIn(arrangement) + PoolReferences.inDock(pool)).filter { it in pool.items }
        val known = shownItems(before).map { it.id.value }.toSet() intersect held.map { it.value }.toSet()
        val afterHome = after.pages.filter { it.type == LauncherPageType.Home }
        val afterItems = editedItems(afterHome, after.dock)
        val idMap = ArrangementIngest.assignIds(afterItems, known, pool, ids)
        val upserts = afterItems.mapNotNull { ArrangementIngest.upsert(it, idMap, pool) }
        val items = pool.items + upserts.associateBy { it.id }
        val seen = HashSet<String>()
        val pages = afterHome.map { ArrangementIngest.toPage(it, idMap, seen) }
        val inDock = HashSet<String>()
        val dock =
            PoolDock(
                pins =
                    after.dock.items.mapNotNull { item ->
                        idMap[item.id.value]?.takeIf { inDock.add(item.id.value) }
                    },
                panel = after.dock.panel?.let { ArrangementIngest.toPage(it, idMap, inDock) },
                settings = PoolDockSettings.of(after.dock, pool.dock?.settings?.libraryPosition),
            )
        val raw =
            pool.copy(
                items = items,
                arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = pages)),
                dock = dock,
            )
        val exclusive = keepDockExclusive(raw, workspaceId, ids)
        return PoolGc.finish(pool, PoolValidation.repair(exclusive).pool)
    }

    /** Every item a projected layout shows: `Home` pages, dock pins, dock panel. */
    private fun shownItems(layout: HomeLayout): List<LauncherItem> =
        layout.pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items } +
            layout.dock.items + layout.dock.panel?.items.orEmpty()

    /**
     * First occurrences by id of the edited items: placed page items, every pin (pins have no cell), placed panel
     * items.
     */
    private fun editedItems(
        home: List<LauncherPage>,
        dock: DockModel,
    ): List<LauncherItem> =
        (
            home.flatMap { it.items }.filter { it.placement != null } +
                dock.items +
                dock.panel?.items.orEmpty().filter { it.placement != null }
        ).filter { it.id.value.isNotBlank() }.distinctBy { it.id.value }

    /**
     * Restores "dock items are referenced by no arrangement" after an edit put an item into the dock that another
     * arrangement (not [workspaceId], whose pages the edit just rewrote) still shows.
     */
    private fun keepDockExclusive(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        ids: WorkspaceIdFactory,
    ): PlacedItemPool {
        val dock = pool.dock
        val elsewhere =
            pool.arrangements.filterKeys { it != workspaceId }.values.flatMap(PoolReferences::referencedIn).toSet()
        val contested = dock?.referenced?.filter { it in elsewhere }.orEmpty()
        return if (dock == null || contested.isEmpty()) pool else resolve(pool, workspaceId, dock, contested, ids)
    }

    /** Clones contested apps and folders for the dock; moves contested widget instances out of other arrangements. */
    private fun resolve(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        dock: PoolDock,
        contested: List<PoolItemId>,
        ids: WorkspaceIdFactory,
    ): PlacedItemPool {
        val (widgets, others) = contested.partition { pool.items[it] is PoolWidget }
        val taken = pool.items.keys.toMutableSet()
        val clones = others.associateWith { PoolIds.fresh(taken, ids).also { fresh -> taken += fresh } }
        val clonedItems = clones.mapNotNull { (old, new) -> pool.items[old]?.let { cloneAs(it, new) } }
        val remapped =
            dock.copy(
                pins = dock.pins.map { clones[it] ?: it },
                panel =
                    dock.panel?.let { page ->
                        page.copy(
                            placements = page.placements.map { p -> clones[p.item]?.let { p.copy(item = it) } ?: p },
                        )
                    },
            )
        val moved = widgets.toSet()
        val arrangements =
            pool.arrangements.mapValues { (id, arrangement) ->
                if (id == workspaceId || moved.isEmpty()) {
                    arrangement
                } else {
                    arrangement.copy(
                        pages =
                            arrangement.pages.map { page ->
                                page.copy(placements = page.placements.filter { it.item !in moved })
                            },
                    )
                }
            }
        return pool.copy(
            items = pool.items + clonedItems.associateBy { it.id },
            arrangements = arrangements,
            dock = remapped,
        )
    }

    private fun cloneAs(
        item: PoolItem,
        id: PoolItemId,
    ): PoolItem =
        when (item) {
            is PoolApp -> item.copy(id = id)
            is PoolFolder -> item.copy(id = id)
            is PoolWidget -> item
        }

    private fun mergeHomePages(
        base: List<LauncherPage>,
        home: List<LauncherPage>,
    ): List<LauncherPage> {
        val queue = ArrayDeque(home)
        val lastHome = base.indexOfLast { it.type == LauncherPageType.Home }
        val out = ArrayList<LauncherPage>(base.size + home.size)
        if (lastHome < 0) {
            out += queue
            queue.clear()
        }
        base.forEachIndexed { index, page ->
            if (page.type == LauncherPageType.Home) queue.removeFirstOrNull()?.let(out::add) else out += page
            if (index == lastHome) {
                out += queue
                queue.clear()
            }
        }
        return out
    }

    private fun standIn(base: HomeLayout): LauncherPage =
        LauncherPage(
            id = base.pages.firstOrNull { it.type == LauncherPageType.Home }?.id ?: LauncherPageId(STAND_IN_PAGE_ID),
            grid = base.settings.grid.dimensions,
        )

    private const val STAND_IN_PAGE_ID = "home"
}

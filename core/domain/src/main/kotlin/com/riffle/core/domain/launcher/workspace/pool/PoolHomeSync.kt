package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.allItems
import com.riffle.core.domain.launcher.home.dockFor
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.HomeLayoutWorkspaceMapper
import com.riffle.core.domain.launcher.workspace.HomeShownWorkspace
import com.riffle.core.domain.launcher.workspace.HomeShownWorkspaces
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * One step of the standard home rendered from the pool (S4 cutover, app half): the pool store after the step, the
 * `HomeLayoutSet` the shell keeps in memory ([shown], every placed item as the pool holds it), and the widget host ids
 * no arrangement and no dock references any more ([releasedHostIds]; delete them only once nothing else holds them,
 * see [PoolHostIds.isReferenced]).
 */
data class PoolHomeCommit(
    val state: PoolStoreState,
    val shown: HomeLayoutSet,
    val releasedHostIds: Set<HostedWidgetId> = emptySet(),
)

/**
 * The whole `HomeLayoutSet` <-> pool store mapping the standard home runs on once it is cut over, built on
 * [PoolHomeProjection]. Pure.
 *
 * Every layout key of the set (`deviceClass`, `mode`) shows the arrangement of the migrated workspace
 * `ws:<layout class>:<mode>` in the pool of its Workspaces layout class (N10: a landscape phone shows the phone pool;
 * rotating changes geometry only), and every device class's dock is the pool's dock. What the pool does not hold
 * (generated and All-apps pages, settings, template, selection, mode choices, Library's dock edge) stays in the set.
 *
 * [commit] is the one write path: every edit the shell's engines made since the last [commit] is ingested into the
 * pool, then every key is projected again, so keys that share an arrangement or a dock never go stale. [stored] is
 * what is still written to the `HomeLayoutSet` store: the same set with no placed items (page slots and dock settings
 * only), so `HomeLayoutSet` stops being written for placed items and the dock.
 */
@Suppress("TooManyFunctions") // The one whole-set mapping: commit, projection, restore, uninstall, new apps.
object PoolHomeSync {
    /** The migrated workspace whose arrangement [key] shows. */
    fun workspaceOf(key: HomeLayoutKey): WorkspaceId =
        HomeLayoutWorkspaceMapper.workspaceId(key.deviceClass.workspaceLayoutClass(), key.viewMode)

    /**
     * Ingests what changed from [previous] (the last [PoolHomeCommit.shown], null at startup or after the pool was
     * replaced) to [next] (the shell's new set) and projects [next] from the result.
     *
     * Which workspace a key shows and edits is [shown]'s answer (S9: the active workspace of the active key's layout,
     * [com.riffle.core.domain.launcher.workspace.WorkspaceHome]; the default is the migrated one). A workspace that
     * borrows an arrangement forks it on its first edit, so the edit lands in its own copy.
     *
     * Per key, the active key first: a key [previous] did not hold is projected when its workspace has an arrangement
     * (its own or a borrowed one), else adopted (its layout becomes the arrangement, keeping engine ids where they are
     * free; the dock is adopted only by a pool that has none). A key whose `Home` pages or dock changed is ingested
     * ([PoolHomeProjection.ingest]); a second key of the same workspace in one commit (portrait and landscape phone)
     * is not, so the active key's edit wins, and once one key wrote a layout's dock the others compare against the
     * pool's dock rather than overwrite it. Selection, edit mode and settings never touch the pool. Library's dock
     * edge is mirrored into the pool dock.
     */
    fun commit(
        state: PoolStoreState,
        previous: HomeLayoutSet?,
        next: HomeLayoutSet,
        ids: WorkspaceIdFactory,
        shown: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): PoolHomeCommit {
        val run = CommitRun(state.pools, ids)
        keysOf(next).forEach { key ->
            val target = shown.shownFor(key, next.activeKey.viewMode)
            run.apply(key, target, previous?.takeIf { key in it.layouts }?.layoutFor(key), next.layoutFor(key))
        }
        val pools = withLibraryEdges(run.pools, next)
        val nextState = if (pools == state.pools) state else state.copy(pools = pools)
        return PoolHomeCommit(nextState, project(pools, next, shown), run.released)
    }

    /**
     * [next] as the pool shows it, ingesting and adopting nothing: the `previous` of a [commit] when the shell holds no
     * projection yet (a fresh install, where the shell starts from a default set the pool never showed).
     */
    fun projection(
        state: PoolStoreState,
        next: HomeLayoutSet,
        shown: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): HomeLayoutSet {
        // Only keys whose layout class has a pool: a key without one must stay unseen, so [commit] adopts it.
        val projected = project(state.pools, next, shown)
        return projected.copy(
            layouts = projected.layouts.filterKeys { it.deviceClass.workspaceLayoutClass() in state.pools },
        )
    }

    /** [shown] projected again from [state], ingesting nothing it already shows (after the pool changed elsewhere). */
    fun reproject(
        state: PoolStoreState,
        shown: HomeLayoutSet,
        ids: WorkspaceIdFactory,
        workspaces: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): PoolHomeCommit = commit(state, null, shown, ids, workspaces)

    /**
     * A confirmed package-removed event: drops the package from every pool ([PoolRemoval.pruneUninstalled]: apps,
     * folder entries, dock pins, emptied folders; widgets are left alone) and projects [shown] again.
     */
    fun pruneUninstalled(
        state: PoolStoreState,
        shown: HomeLayoutSet,
        packageName: AppPackageName,
        profile: AppProfile,
        ids: WorkspaceIdFactory,
        workspaces: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): PoolHomeCommit {
        val released = LinkedHashSet<HostedWidgetId>()
        val pools =
            state.pools.mapValues { (_, pool) ->
                PoolRemoval.pruneUninstalled(pool, packageName, profile).also { released += it.releasedHostIds }.pool
            }
        val pruned = if (pools == state.pools) state else state.copy(pools = pools)
        val projected = reproject(pruned, shown, ids, workspaces)
        return projected.copy(releasedHostIds = released + projected.releasedHostIds)
    }

    /**
     * Restoring a backup once the home renders from the pool. A backup written by a cut-over build ([homeFromPool])
     * restores its pool section as the authority (the backup's pool carries no host ids, so the standard home's widgets
     * take theirs back from [homeLayouts], which a cut-over build exports as the projection, by item id). Any other
     * backup (older builds, with or without a pool section) re-imports its [homeLayouts] into the pool
     * ([PoolCutover.resync] over [backupPool] or, without one, the [current] pool, so arrangements the backup does not
     * describe are kept). [homeLayouts] is then projected. [PoolHomeCommit.releasedHostIds] holds the host ids of the
     * [current] widgets the restored pool no longer references (to delete once nothing else holds them).
     */
    fun restore(
        current: PoolStoreState?,
        backupPool: PoolStoreState?,
        homeLayouts: HomeLayoutSet,
        homeFromPool: Boolean,
        ids: WorkspaceIdFactory,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
        workspaces: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): PoolHomeCommit {
        val state =
            if (homeFromPool && backupPool != null) {
                withHostIdsFrom(
                    PoolLayoutFold.fold(backupPool).copy(migrated = true, dockImportPending = false),
                    homeLayouts,
                )
            } else {
                PoolCutover.resync(backupPool ?: current, homeLayouts, providerOf).state
            }
        val restored = commit(state, null, homeLayouts, ids, workspaces)
        val dropped =
            current?.let { PoolHostIds.referencedHostIds(it) - PoolHostIds.referencedHostIds(restored.state) }.orEmpty()
        return restored.copy(releasedHostIds = restored.releasedHostIds + dropped)
    }

    /**
     * N9/N12 on the standard home: a package was installed ([arrival]; only [AppArrival.INSTALLED] places anything).
     * One app is placed in every opted-in workspace ([PoolNewApps.placeNewApp]) of every layout that has a pool and a
     * stored workspace layout in [workspaceSet]; a workspace that borrows forks first (with the same candidates the
     * standard home shows it with). [shown] is then projected again. Nothing changes for any other arrival, for a
     * layout without stored workspaces, or when no workspace opts in.
     */
    @Suppress("LongParameterList") // The install event's facts plus the projection's inputs.
    fun placeNewApp(
        state: PoolStoreState,
        shown: HomeLayoutSet,
        identity: AppIdentity,
        label: String,
        workspaceSet: WorkspaceSet,
        arrival: AppArrival,
        ids: WorkspaceIdFactory,
        workspaces: HomeShownWorkspaces = HomeShownWorkspaces.Migrated,
    ): PoolHomeCommit {
        val pools =
            state.pools.mapValues { (layoutClass, pool) ->
                if (!workspaceSet.hasLayout(layoutClass)) {
                    pool
                } else {
                    PoolNewApps.placeNewApp(
                        pool = pool,
                        identity = identity,
                        label = label,
                        ids = ids,
                        layout = workspaceSet.workspacesFor(layoutClass),
                        arrival = arrival,
                    ) { id -> WorkspaceHome.borrowCandidates(id, layoutClass) }.edit.pool
                }
            }
        val placed = if (pools == state.pools) state else state.copy(pools = pools)
        return reproject(placed, shown, ids, workspaces)
    }

    /**
     * What the `HomeLayoutSet` store still gets once the home renders from the pool: [shown] without placed items in
     * the layouts and docks of the device classes [pooled] covers (through [workspaceLayoutClass]). `Home` pages stay
     * as empty slots (so their order among generated pages and the selection survive), the dock keeps its settings
     * and an empty panel page. Everything else is kept as it is.
     */
    fun stored(
        shown: HomeLayoutSet,
        pooled: Set<HomeLayoutDeviceClass>,
    ): HomeLayoutSet {
        fun isPooled(deviceClass: HomeLayoutDeviceClass) = deviceClass.workspaceLayoutClass() in pooled
        return shown.copy(
            layouts =
                shown.layouts.mapValues { (key, layout) ->
                    if (isPooled(key.deviceClass)) layout.withoutPlacedItems() else layout
                },
            docks = shown.docks.mapValues { (deviceClass, dock) -> if (isPooled(deviceClass)) dock.empty() else dock },
        )
    }

    /** Every key [set] holds a layout for, and its active key; the active key first. */
    private fun keysOf(set: HomeLayoutSet): List<HomeLayoutKey> =
        listOf(set.activeKey) + set.layouts.keys.filter { it != set.activeKey }

    /**
     * The arrangement [target] draws in [pool]: its own, else the first of its candidates with pages (it borrows that
     * one until its first edit forks it), else its own id (no arrangement: no placed pages, a stand-in page).
     */
    fun arrangementShown(
        pool: PlacedItemPool,
        target: HomeShownWorkspace,
    ): WorkspaceId =
        target.workspaceId.takeIf { it in pool.arrangements }
            ?: PoolHomeView.arrangementOf(pool, target.candidates)
            ?: target.workspaceId

    /** [next] with each key's `Home` pages and each device class's dock projected from [pools]. */
    private fun project(
        pools: Map<HomeLayoutDeviceClass, PlacedItemPool>,
        next: HomeLayoutSet,
        shown: HomeShownWorkspaces,
    ): HomeLayoutSet {
        val keys = keysOf(next)
        val layouts =
            keys.associateWith { key ->
                val base = next.layoutFor(key)
                val pool = pools[key.deviceClass.workspaceLayoutClass()]
                val target = shown.shownFor(key, next.activeKey.viewMode)
                if (pool == null) base else PoolHomeProjection.project(pool, arrangementShown(pool, target), base)
            }
        val docks =
            (next.docks.keys + keys.map { it.deviceClass }).associateWith { deviceClass ->
                pools[deviceClass.workspaceLayoutClass()]?.let(PoolHomeProjection::projectDock)
                    ?: next.dockFor(deviceClass)
            }
        return next.copy(layouts = layouts, docks = docks)
    }

    /** One [commit]'s ingests, in key order. */
    private class CommitRun(
        initial: Map<HomeLayoutDeviceClass, PlacedItemPool>,
        private val ids: WorkspaceIdFactory,
    ) {
        val pools = LinkedHashMap(initial)
        val released = LinkedHashSet<HostedWidgetId>()
        private val written = HashSet<WorkspaceId>()
        private val dockWritten = HashSet<HomeLayoutDeviceClass>()

        fun apply(
            key: HomeLayoutKey,
            target: HomeShownWorkspace,
            previous: HomeLayout?,
            next: HomeLayout,
        ) {
            val layoutClass = key.deviceClass.workspaceLayoutClass()
            val workspaceId = target.workspaceId
            val pool = pools[layoutClass]
            when {
                workspaceId in written -> Unit
                previous == null ->
                    // A key never seen is adopted only when its workspace has nothing to show: no arrangement of its
                    // own and none to borrow (a borrower shows the borrowed one and forks on its first edit).
                    if (pool == null || arrangementShown(pool, target) !in pool.arrangements) {
                        adopt(layoutClass, workspaceId, pool ?: PlacedItemPool(), next)
                    }
                else -> edit(layoutClass, target, pool ?: PlacedItemPool(), previous, next)
            }
        }

        /** [layout] becomes [workspaceId]'s arrangement; a pool that already has a dock keeps it. */
        private fun adopt(
            layoutClass: HomeLayoutDeviceClass,
            workspaceId: WorkspaceId,
            pool: PlacedItemPool,
            layout: HomeLayout,
        ) {
            val dock = PoolHomeProjection.projectDock(pool)
            val after = if (dock == null) layout else layout.copy(dock = dock)
            val before =
                after.copy(
                    pages = after.pages.map { it.withoutHomeItems() },
                    dock = dock ?: after.dock.empty(),
                )
            ingest(layoutClass, workspaceId, pool, before, after)
            if (dock == null) dockWritten += layoutClass
        }

        private fun edit(
            layoutClass: HomeLayoutDeviceClass,
            target: HomeShownWorkspace,
            pool: PlacedItemPool,
            previous: HomeLayout,
            next: HomeLayout,
        ) {
            val shared = if (layoutClass in dockWritten) PoolHomeProjection.projectDock(pool) else null
            val before = shared?.let { previous.copy(dock = it) } ?: previous
            val after = shared?.let { next.copy(dock = it) } ?: next
            if (before.homePages() != after.homePages() || before.dock != after.dock) {
                val fork = forkedIfBorrowing(pool, target)
                ingest(
                    layoutClass,
                    target.workspaceId,
                    fork.pool,
                    before.withItemIds(fork.clonedIds),
                    after.withItemIds(fork.clonedIds),
                )
                dockWritten += layoutClass
            }
        }

        /**
         * Fork on first edit (S4 domain, used by the standard home from S9): a workspace that shows a borrowed
         * arrangement gets its own copy first, so the edit lands in it and the borrowed one (the migrated home, a
         * recovery copy) is left as it was. Apps and widget instances are shared by reference, so their ids in the
         * edited layout stay valid; folders are cloned, so [ForkedPool.clonedIds] maps the borrowed folder ids the
         * projection showed to the fork's own, and the edit is applied to those.
         */
        private fun forkedIfBorrowing(
            pool: PlacedItemPool,
            target: HomeShownWorkspace,
        ): ForkedPool {
            val borrows = target.workspaceId !in pool.arrangements && target.candidates.isNotEmpty()
            val result = if (borrows) PoolArrangements.fork(pool, target.workspaceId, target.candidates, ids) else null
            val forked = (result as? PoolResult.Done)?.edit?.pool ?: pool
            val source = PoolHomeView.arrangementOf(pool, target.candidates.filter { it != target.workspaceId })
            val from = source?.let { pool.arrangements[it] }
            val to = forked.arrangements[target.workspaceId]
            return if (forked === pool || from == null || to == null) {
                ForkedPool(forked)
            } else {
                ForkedPool(forked, clonedIds(from, to))
            }
        }

        private fun ingest(
            layoutClass: HomeLayoutDeviceClass,
            workspaceId: WorkspaceId,
            pool: PlacedItemPool,
            before: HomeLayout,
            after: HomeLayout,
        ) {
            val edit = PoolHomeProjection.ingest(pool, workspaceId, before, after, ids)
            pools[layoutClass] = edit.pool
            released += edit.releasedHostIds
            written += workspaceId
        }
    }
}

/** A pool after a fork on first edit, and the ids the fork gave its cloned items (old text to new text). */
private class ForkedPool(
    val pool: PlacedItemPool,
    val clonedIds: Map<String, String> = emptyMap(),
)

/** Old to new item id for every placement the copy [to] of [from] re-pointed (pages and placements keep order). */
private fun clonedIds(
    from: Arrangement,
    to: Arrangement,
): Map<String, String> =
    from.pages.zip(to.pages).flatMap { (old, new) ->
        old.placements.zip(new.placements).mapNotNull { (a, b) ->
            (a.item.value to b.item.value).takeIf { a.item != b.item }
        }
    }.toMap()

/** This layout with the top-level `Home` page items in [ids] given their new id (a fork's clones). */
private fun HomeLayout.withItemIds(ids: Map<String, String>): HomeLayout =
    if (ids.isEmpty()) {
        this
    } else {
        copy(
            pages =
                pages.map { page ->
                    if (page.type != LauncherPageType.Home) {
                        page
                    } else {
                        page.copy(items = page.items.map { item -> ids[item.id.value]?.let(item::withId) ?: item })
                    }
                },
        )
    }

private fun LauncherItem.withId(value: String): LauncherItem =
    when (this) {
        is AppShortcutItem -> copy(id = LauncherItemId(value))
        is FolderItem -> copy(id = LauncherItemId(value))
        is WidgetItem -> copy(id = LauncherItemId(value))
    }

/**
 * Library's dock edge is a `HomeLayoutSet` setting the dock settings UI writes; the pool dock keeps a copy
 * ([PoolDockSettings.libraryPosition]) so the dock needs nothing else. The active device class's edge wins for its
 * layout class.
 */
private fun withLibraryEdges(
    pools: Map<HomeLayoutDeviceClass, PlacedItemPool>,
    next: HomeLayoutSet,
): Map<HomeLayoutDeviceClass, PlacedItemPool> =
    pools.mapValues { (layoutClass, pool) ->
        val dock = pool.dock
        val active = next.activeKey.deviceClass
        val source = if (active.workspaceLayoutClass() == layoutClass) active else layoutClass
        val edge = next.libraryDockEdgesByDeviceClass[source]
        if (dock == null || dock.settings.libraryPosition == edge) {
            pool
        } else {
            pool.copy(dock = dock.copy(settings = dock.settings.copy(libraryPosition = edge)))
        }
    }

/** Gives unbound pool widgets the host id [homeLayouts] shows them with (same item id, same layout class). */
private fun withHostIdsFrom(
    state: PoolStoreState,
    homeLayouts: HomeLayoutSet,
): PoolStoreState {
    val hostIds = HashMap<HomeLayoutDeviceClass, MutableMap<PoolItemId, HostedWidgetId>>()
    val widgets =
        homeLayouts.layouts.map { (key, layout) -> key.deviceClass to layout.allItems() } +
            homeLayouts.docks.map { (deviceClass, dock) -> deviceClass to dock.items + dock.panel?.items.orEmpty() }
    widgets.forEach { (deviceClass, items) ->
        items.filterIsInstance<WidgetItem>()
            .filter { it.appWidgetId != ArrangementAdapter.UNBOUND_HOST_ID }
            .forEach { widget ->
                val known = hostIds.getOrPut(deviceClass.workspaceLayoutClass()) { HashMap() }
                known[PoolItemId(widget.id.value)] = widget.appWidgetId
            }
    }
    val pools =
        state.pools.mapValues { (layoutClass, pool) ->
            val known = hostIds[layoutClass].orEmpty()
            val items =
                pool.items.mapValues { (id, item) ->
                    val hostId = known[id]
                    if (item is PoolWidget && item.hostedId == null && hostId != null) {
                        item.copy(
                            hostedId = hostId,
                        )
                    } else {
                        item
                    }
                }
            if (items == pool.items) pool else PoolValidation.repair(pool.copy(items = items)).pool
        }
    return state.copy(pools = pools)
}

private fun HomeLayout.withoutPlacedItems(): HomeLayout =
    copy(pages = pages.map { it.withoutHomeItems() }, dock = dock.empty())

private fun LauncherPage.withoutHomeItems(): LauncherPage =
    if (type == LauncherPageType.Home && items.isNotEmpty()) copy(items = emptyList()) else this

private fun DockModel.empty(): DockModel =
    if (items.isEmpty() && panel?.items.isNullOrEmpty()) {
        this
    } else {
        copy(
            items = emptyList(),
            panel = panel?.copy(items = emptyList()),
        )
    }

private fun HomeLayout.homePages(): List<LauncherPage> = pages.filter { it.type == LauncherPageType.Home }

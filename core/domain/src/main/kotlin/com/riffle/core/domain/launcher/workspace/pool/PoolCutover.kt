package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

data class PoolCutoverResult(
    val state: PoolStoreState,
    /** True when [state] differs from what was stored, i.e. it should be written. */
    val changed: Boolean,
    /** What the migration could not carry over (ids only). */
    val issues: List<PoolIssue>,
)

/**
 * The one-time, flag-guarded import of the placed items in [HomeLayoutSet] into the pool store (S4 of the
 * Workspaces work). Pure: [HomeLayoutSet] is only read, never changed or deleted, so it stays the source of
 * truth for the standard home until the app cuts over to the pool ([PoolHomeProjection]).
 *
 * Idempotent: once [PoolStoreState.migrated] is set the stored state is returned as is, except that a store migrated
 * before the dock moved into the pool ([PoolStoreState.dockImportPending], a schema 1 value) gets each layout's dock
 * imported once ([PoolMigration.importDock]); its arrangements, possibly edited since, are kept. Before the flag is
 * set, a device class that already holds a non-empty pool keeps it (stored data is never overwritten; it gets a dock
 * if it has none), every other class gets [PoolMigration]'s pool (dock included), and every migrated widget whose host
 * id [providerOf] can name gets its provider backfilled (see [PoolWidgetBackfill]).
 */
object PoolCutover {
    fun ensureMigrated(
        stored: PoolStoreState?,
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): PoolCutoverResult {
        if (stored?.migrated == true) {
            return if (stored.dockImportPending) importDocks(stored, layoutSet, providerOf) else unchanged(stored)
        }
        val migration = PoolMigration.migrate(layoutSet)
        val issues = migration.issues.toMutableList()
        val kept =
            stored?.pools.orEmpty().filterValues { !it.isEmpty }.mapValues { (deviceClass, pool) ->
                withDock(pool, layoutSet, deviceClass, issues)
            }
        val pools =
            (migration.pools + kept).mapValues { (_, pool) -> PoolWidgetBackfill.apply(pool, providerOf) }
        val next = PoolStoreState(migrated = true, pools = pools)
        return PoolCutoverResult(next, changed = next != stored, issues = issues)
    }

    private fun unchanged(stored: PoolStoreState) = PoolCutoverResult(stored, changed = false, issues = emptyList())

    /**
     * The one-time dock import for a store migrated under schema 1: every stored class, and every class with a layout.
     */
    private fun importDocks(
        stored: PoolStoreState,
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    ): PoolCutoverResult {
        val issues = mutableListOf<PoolIssue>()
        val classes = stored.pools.keys + layoutSet.layouts.keys.map { it.deviceClass }
        val pools =
            classes.associateWith { deviceClass ->
                val pool = withDock(stored.pools[deviceClass] ?: PlacedItemPool(), layoutSet, deviceClass, issues)
                PoolWidgetBackfill.apply(pool, providerOf)
            }
        val next = stored.copy(pools = pools, dockImportPending = false)
        return PoolCutoverResult(next, changed = true, issues = issues)
    }

    private fun withDock(
        pool: PlacedItemPool,
        layoutSet: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
        issues: MutableList<PoolIssue>,
    ): PlacedItemPool =
        if (pool.dock != null) {
            pool
        } else {
            val imported = PoolMigration.importDock(pool, layoutSet, deviceClass)
            val repaired = PoolValidation.repair(imported.pool)
            issues += imported.issues + repaired.issues
            repaired.pool
        }

    /**
     * The import that hands the standard home to the pool (S4 cutover, and restoring a backup that predates it):
     * [layoutSet] is authoritative for the migrated workspaces (`ws:<deviceclass>:<mode>`), so their arrangements and
     * each layout's dock are replaced by a fresh [PoolMigration] of it, while everything else [stored] holds (the
     * preview's own and forked arrangements, the items only they reference) is kept. Fresh items overwrite stored
     * items with the same (deterministic) id, the fresh arrangements come first so a host id both claim stays with the
     * standard home's widget, and the result is repaired and collected. Pure and deterministic: a second run over the
     * result is equal. [stored] is folded (N10) first; a null [stored] is a plain migration.
     */
    fun resync(
        stored: PoolStoreState?,
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): PoolCutoverResult {
        val migration = PoolMigration.migrate(layoutSet)
        val base = stored?.let(PoolLayoutFold::fold)?.pools.orEmpty()
        val issues = migration.issues.toMutableList()
        val pools =
            (base.keys + migration.pools.keys).sortedBy { it.ordinal }.associateWith { deviceClass ->
                val kept = base[deviceClass]
                val fresh = migration.pools[deviceClass]
                val merged =
                    when {
                        kept == null -> fresh ?: PlacedItemPool()
                        fresh == null -> kept
                        else -> merge(kept, fresh, issues)
                    }
                PoolWidgetBackfill.apply(merged, providerOf)
            }
        val next = PoolStoreState(migrated = true, pools = pools)
        return PoolCutoverResult(next, changed = next != stored, issues = issues)
    }

    private fun merge(
        kept: PlacedItemPool,
        fresh: PlacedItemPool,
        issues: MutableList<PoolIssue>,
    ): PlacedItemPool {
        // A replaced arrangement keeps its own settings (the new-app placement); only its pages are re-imported.
        val replaced =
            fresh.arrangements.mapValues { (id, arrangement) ->
                kept.arrangements[id]?.let { arrangement.copy(newAppPlacement = it.newAppPlacement) } ?: arrangement
            }
        val repaired =
            PoolValidation.repair(
                kept.copy(
                    items = kept.items + fresh.items,
                    arrangements = replaced + kept.arrangements.filterKeys { it !in fresh.arrangements },
                    dock = fresh.dock ?: kept.dock,
                ),
            )
        issues += repaired.issues.filter { it.kind != PoolIssueKind.ORPHAN_ITEM }
        return repaired.pool
    }

    /**
     * A fresh import that replaces every pool (the flag stays set). The standard home is still edited in
     * [HomeLayoutSet], so this is how the pool catches up with it until pool editing exists.
     */
    fun reimport(
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): PoolCutoverResult {
        val migration = PoolMigration.migrate(layoutSet)
        val pools = migration.pools.mapValues { (_, pool) -> PoolWidgetBackfill.apply(pool, providerOf) }
        return PoolCutoverResult(
            PoolStoreState(migrated = true, pools = pools),
            changed = true,
            issues = migration.issues,
        )
    }
}

/**
 * Names the provider of migrated widgets, which `HomeLayoutSet` never recorded (only the host id). A widget that
 * already has a provider, is an unbound placeholder, or whose host id [providerOf] cannot resolve is left as it is.
 */
object PoolWidgetBackfill {
    fun apply(
        pool: PlacedItemPool,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    ): PlacedItemPool {
        val items =
            pool.items.mapValues { (_, item) ->
                val hostedId = (item as? PoolWidget)?.hostedId
                val provider =
                    if (item is PoolWidget && item.provider == null && hostedId != null) {
                        providerOf(
                            hostedId,
                        )
                    } else {
                        null
                    }
                if (item is PoolWidget && provider != null) item.copy(provider = provider) else item
            }
        return if (items == pool.items) pool else pool.copy(items = items)
    }
}

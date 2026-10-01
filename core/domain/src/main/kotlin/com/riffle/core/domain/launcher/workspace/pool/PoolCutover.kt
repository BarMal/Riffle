package com.riffle.core.domain.launcher.workspace.pool

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
 * truth for the standard home until pool editing exists.
 *
 * Idempotent: once [PoolStoreState.migrated] is set the stored state is returned as is. Before that, a device
 * class that already holds a non-empty pool keeps it (stored data is never overwritten), every other class
 * gets [PoolMigration]'s pool, and every migrated widget whose host id [providerOf] can name gets its
 * provider backfilled (see [PoolWidgetBackfill]).
 */
object PoolCutover {
    fun ensureMigrated(
        stored: PoolStoreState?,
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): PoolCutoverResult {
        if (stored?.migrated == true) return PoolCutoverResult(stored, changed = false, issues = emptyList())
        val migration = PoolMigration.migrate(layoutSet)
        val kept = stored?.pools.orEmpty().filterValues { !it.isEmpty }
        val pools =
            (migration.pools + kept).mapValues { (_, pool) -> PoolWidgetBackfill.apply(pool, providerOf) }
        val next = PoolStoreState(migrated = true, pools = pools)
        return PoolCutoverResult(next, changed = next != stored, issues = migration.issues)
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

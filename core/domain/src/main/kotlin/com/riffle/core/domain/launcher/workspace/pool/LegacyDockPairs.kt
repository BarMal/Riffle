package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.layouts.AppLayout

/**
 * The migration of the Dock's app pairs to layouts (ADR 0003, Amendment 7). An app pair used to be a pinned item of the
 * Dock ([PoolAppPair], stored as kind "pair"); layouts are a Stream stack now and nothing writes a pair any more, but
 * a stored pool or backup may still hold one. The first time it is read each pair becomes an [AppLayout] (in Dock
 * order, the first app left) and leaves the Dock. Idempotent: a state without pairs comes back unchanged.
 */
object LegacyDockPairs {
    /** [state] without any pair, and the layouts they become: the Dock's pins in order, then any other pair. */
    data class Extraction(
        val state: PoolStoreState,
        val layouts: List<AppLayout>,
    ) {
        val changed: Boolean get() = layouts.isNotEmpty() || state.pools.values.any { it.hasPair() }
    }

    fun extract(state: PoolStoreState): Extraction {
        val layouts = ArrayList<AppLayout>()
        val pools =
            state.pools.mapValues { (_, pool) ->
                val pairs = pool.pairsInDockOrder()
                if (pairs.isEmpty()) {
                    pool
                } else {
                    pairs.forEach { pair ->
                        layouts += AppLayout(pair.id.value, pair.first.appIdentity, pair.second.appIdentity)
                    }
                    pool.withoutPairs(pairs.map { it.id }.toSet())
                }
            }
        // The same ordered pair on several device classes' docks is one layout.
        val distinct = layouts.distinctBy { it.pair }
        return Extraction(if (pools == state.pools) state else state.copy(pools = pools), distinct)
    }

    private fun PlacedItemPool.hasPair(): Boolean = items.values.any { it is PoolAppPair }

    private fun PlacedItemPool.pairsInDockOrder(): List<PoolAppPair> {
        val pairs = items.values.filterIsInstance<PoolAppPair>().associateBy { it.id }
        val pinned = dock?.pins.orEmpty().mapNotNull { pairs[it] }
        return pinned + pairs.values.filter { it !in pinned }
    }

    private fun PlacedItemPool.withoutPairs(ids: Set<PoolItemId>): PlacedItemPool =
        copy(
            items = items - ids,
            dock = dock?.copy(pins = dock.pins.filterNot { it in ids }),
        )
}

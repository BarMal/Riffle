package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.pairs.AppPair

/**
 * The migration of the Dock's app pairs to pairs (ADR 0003, Amendment 7). An app pair used to be a pinned item of the
 * Dock (stored as kind "pair"); pairs are a Stream stack now and nothing writes a pair any more, but a stored pool or
 * backup may still hold one, and decoding keeps it in [PlacedItemPool.legacyPairs]. The first time it is read each
 * pair becomes an [AppPair] (in Dock order, the first app left) and the pool no longer has it. Idempotent: a state
 * without pairs comes back unchanged.
 */
object LegacyDockPairs {
    /** [state] without any pair, and the pairs they become, in Dock order. */
    data class Extraction(
        val state: PoolStoreState,
        val pairs: List<AppPair>,
    ) {
        val changed: Boolean get() = pairs.isNotEmpty() || state.pools.values.any { it.legacyPairs.isNotEmpty() }
    }

    fun extract(state: PoolStoreState): Extraction {
        if (state.pools.values.none { it.legacyPairs.isNotEmpty() }) return Extraction(state, emptyList())
        val pairs =
            state.pools.values.flatMap { pool ->
                pool.legacyPairs.map { AppPair(it.id.value, it.first.appIdentity, it.second.appIdentity) }
            }
        val pools = state.pools.mapValues { (_, pool) -> pool.copy(legacyPairs = emptyList()) }
        // The same ordered pair on several device classes' docks is one pair.
        return Extraction(state.copy(pools = pools), pairs.distinctBy { it.sides })
    }
}

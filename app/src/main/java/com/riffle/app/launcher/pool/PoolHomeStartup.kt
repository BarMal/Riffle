package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import kotlinx.coroutines.CancellationException

/**
 * The `HomeLayoutSet` store as the cutover needs it: the set itself and the one-time snapshot of it taken when the
 * home moved to the pool. The snapshot is the marker that the stored set no longer holds placed items, and the source
 * the migration falls back to should the pool ever be lost.
 */
internal interface HomeLayoutCutoverStore {
    suspend fun read(): HomeLayoutSet?

    suspend fun write(layoutSet: HomeLayoutSet)

    suspend fun readPrePoolSnapshot(): HomeLayoutSet?

    /** Stores [snapshot], or removes it when null. */
    suspend fun writePrePoolSnapshot(snapshot: HomeLayoutSet?)
}

/** How the home opens this process: what the `HomeLayoutSet` repository starts from, and who renders placed items. */
internal data class HomeLayoutOpening(
    val stored: HomeLayoutSet?,
    /** True when the standard home renders from the pool ([PoolHomeLayoutRepository]); false: the legacy path. */
    val poolHome: Boolean,
)

/**
 * Opens the home at process start (once, before the first frame, off the main thread like the existing load).
 *
 * With the cutover switch on ([enabled], the default):
 * - **First start after the cutover** (no snapshot yet): the pool is loaded and brought in line with the stored set
 *   ([CachedPoolRepository.initializeForCutover]), written, and only once that write has landed is the full stored set
 *   kept as the snapshot. Until both have landed the home stays on the legacy path for this process, so a failing
 *   store never leaves the user with a stripped `HomeLayoutSet` and no pool.
 * - **Later starts**: the pool is loaded as stored (a pool that cannot be decoded is re-imported from the snapshot,
 *   not from the stripped set) and renders the home.
 *
 * With the switch off: a store that was cut over is handed back once (the pool's projection is written as the full
 * `HomeLayoutSet` and the snapshot removed), so the legacy path finds every item where it left it; a pool that cannot
 * be read keeps the pool home for this process instead (the stored set has no items to fall back to). Nothing here
 * throws: a store failure falls back as described.
 */
internal suspend fun openHomeLayouts(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    enabled: Boolean,
    ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
): HomeLayoutOpening {
    val stored = store.read()
    val snapshot = guarded { store.readPrePoolSnapshot() }
    return when {
        snapshot != null && enabled -> {
            pool.initialize(snapshot, providerOf)
            HomeLayoutOpening(stored ?: snapshot, poolHome = pool.current() != null)
        }
        snapshot != null -> handBack(store, pool, stored ?: snapshot, snapshot, ids, providerOf)
        enabled -> cutOver(store, pool, stored, providerOf)
        else -> HomeLayoutOpening(stored, poolHome = false)
    }
}

private suspend fun cutOver(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    val source = stored ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    val ready =
        guarded {
            pool.initializeForCutover(source, providerOf) && store.writePrePoolSnapshot(source).let { true }
        } == true
    return HomeLayoutOpening(stored, poolHome = ready)
}

private suspend fun handBack(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet,
    snapshot: HomeLayoutSet,
    ids: WorkspaceIdFactory,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    pool.initialize(snapshot, providerOf)
    val state = pool.current()
    if (state == null || !pool.isPersistent) return HomeLayoutOpening(stored, poolHome = state != null)
    val full = PoolHomeSync.reproject(state, stored, ids).shown
    val handedBack =
        guarded {
            store.write(full)
            store.writePrePoolSnapshot(null)
        } != null
    return if (handedBack) HomeLayoutOpening(full, poolHome = false) else HomeLayoutOpening(stored, poolHome = true)
}

/** [block]'s result, or null when a store failed (the caller falls back; nothing is logged: no item content). */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun <T> guarded(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        null
    }

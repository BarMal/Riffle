package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PoolCutover
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import kotlinx.coroutines.CancellationException

/** What the snapshot key holds: nothing, a readable set, or a value that is there but cannot be read. */
internal sealed interface PrePoolSnapshot {
    data object Absent : PrePoolSnapshot

    data class Present(val layoutSet: HomeLayoutSet) : PrePoolSnapshot

    /** A value is stored but cannot be decoded, or the read failed: the home was cut over, so never cut over again. */
    data object Unreadable : PrePoolSnapshot
}

/**
 * The `HomeLayoutSet` store as the cutover needs it: the set itself and the snapshot taken when the home moved to the
 * pool. The snapshot is the marker that the stored set no longer holds placed items, and the recovery copy the
 * migration falls back to should the pool be lost; the pool-backed home refreshes it after its writes, so it stays
 * recent. It is never the source of truth while the pool can be read.
 */
internal interface HomeLayoutCutoverStore {
    suspend fun read(): HomeLayoutSet?

    suspend fun write(layoutSet: HomeLayoutSet)

    suspend fun readPrePoolSnapshot(): PrePoolSnapshot

    /** Stores [snapshot], or removes it when null. */
    suspend fun writePrePoolSnapshot(snapshot: HomeLayoutSet?)

    /** Copies an unreadable snapshot once to a quarantine key, before anything writes the snapshot key again. */
    suspend fun quarantinePrePoolSnapshot() = Unit
}

/** How the home opens this process: what the `HomeLayoutSet` repository starts from, and who renders placed items. */
internal data class HomeLayoutOpening(
    val stored: HomeLayoutSet?,
    /** True when the standard home renders from the pool ([PoolHomeLayoutRepository]); false: the legacy path. */
    val poolHome: Boolean,
)

/**
 * Opens the home at process start (once, before the first frame, off the main thread like the existing load). The
 * pool home only runs on a pool that can be written ([CachedPoolRepository.isPersistent]); nothing here throws.
 *
 * - **No snapshot, switch on** (first cutover): [CachedPoolRepository.initializeForCutover] resyncs and writes the
 *   pool; only once that landed is the full set kept as the snapshot. Any failure: legacy path, nothing stripped.
 * - **No snapshot, switch off**: the legacy path; the pool is not read.
 * - **Readable snapshot, switch on**: the pool is loaded (an undecodable pool is quarantined and rebuilt from the
 *   recent snapshot). A pool that cannot be read or written: the legacy path from the snapshot's full set, which is
 *   written back with the marker removed, so the next start cuts over cleanly from it.
 * - **Readable snapshot, switch off** (hand-back): the pool's projection is written as the full set and the marker
 *   removed (a pool that cannot be read: the snapshot's set instead).
 * - **Unreadable snapshot**: quarantined, and never cut over again. A readable pool that holds the home runs it (or
 *   is handed back with the switch off); otherwise the legacy path runs on the stored set and nothing is written.
 */
internal suspend fun openHomeLayouts(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    enabled: Boolean,
    ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
): HomeLayoutOpening {
    val stored = store.read()
    return when (val snapshot = guarded { store.readPrePoolSnapshot() } ?: PrePoolSnapshot.Unreadable) {
        is PrePoolSnapshot.Present ->
            if (enabled) {
                openPoolHome(store, pool, stored, snapshot.layoutSet, providerOf)
            } else {
                handBack(store, pool, stored ?: snapshot.layoutSet, snapshot.layoutSet, ids, providerOf)
            }
        PrePoolSnapshot.Unreadable -> openWithoutSnapshot(store, pool, stored, enabled, ids, providerOf)
        PrePoolSnapshot.Absent ->
            if (enabled) cutOver(store, pool, stored, providerOf) else HomeLayoutOpening(stored, poolHome = false)
    }
}

private suspend fun openPoolHome(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    snapshot: HomeLayoutSet,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    pool.initialize(snapshot, providerOf)
    return if (pool.current() != null && pool.isPersistent) {
        HomeLayoutOpening(stored ?: snapshot, poolHome = true)
    } else {
        legacyFrom(store, snapshot)
    }
}

private suspend fun cutOver(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    val source = stored ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    // The snapshot is the pool's projection, not [source]: should [source] be a stripped set whose marker was lost,
    // the resync keeps the pool (PoolCutover's hard guard) and the snapshot still gets every item.
    val ready =
        guarded {
            pool.initializeForCutover(source, providerOf) &&
                pool.current()?.let {
                        state ->
                    store.writePrePoolSnapshot(PoolHomeSync.projection(state, source))
                } != null
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
    if (state == null || !pool.isPersistent) return legacyFrom(store, snapshot)
    val full = PoolHomeSync.reproject(state, stored, ids).shown
    val handedBack =
        guarded {
            store.write(full)
            store.writePrePoolSnapshot(null)
        } != null
    return if (handedBack) HomeLayoutOpening(full, poolHome = false) else HomeLayoutOpening(stored, poolHome = true)
}

/**
 * The snapshot cannot be read, so the store was cut over but the recovery copy is gone: never cut over again (that
 * would resync from a stripped set). A readable pool that holds the home runs it (or is handed back); otherwise the
 * legacy path runs on the stored set this process and nothing is written here.
 */
private suspend fun openWithoutSnapshot(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    enabled: Boolean,
    ids: WorkspaceIdFactory,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    val quarantined = guarded { store.quarantinePrePoolSnapshot() } != null
    val source = stored ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    pool.initialize(source, providerOf)
    val state = pool.current()
    val usable = quarantined && state != null && pool.isPersistent && PoolCutover.holdsHome(state)
    return when {
        !usable || state == null -> HomeLayoutOpening(stored, poolHome = false)
        enabled -> HomeLayoutOpening(source, poolHome = true)
        else -> handBack(store, pool, source, PoolHomeSync.reproject(state, source, ids).shown, ids, providerOf)
    }
}

/**
 * The legacy path for this process from a full [layoutSet] (the snapshot): written back as the `HomeLayoutSet` (never
 * stripped) and the marker removed, so the next start finds a full set and cuts over cleanly from it. A failed write
 * keeps the marker (the next start then trusts the pool again).
 */
private suspend fun legacyFrom(
    store: HomeLayoutCutoverStore,
    layoutSet: HomeLayoutSet,
): HomeLayoutOpening {
    if (guarded { store.write(layoutSet) } != null) guarded { store.writePrePoolSnapshot(null) }
    return HomeLayoutOpening(layoutSet, poolHome = false)
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

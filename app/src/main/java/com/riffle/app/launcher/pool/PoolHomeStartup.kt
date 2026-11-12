package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.pool.PoolCutover
import com.riffle.core.domain.launcher.workspace.pool.PoolDockRepair
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import kotlinx.coroutines.CancellationException

/** What the snapshot key holds: nothing, a readable set, or a value that is there but cannot be read. */
internal sealed interface PrePoolSnapshot {
    data object Absent : PrePoolSnapshot

    data class Present(val layoutSet: HomeLayoutSet) : PrePoolSnapshot

    /** A value is stored but cannot be decoded, or the read failed: the home was cut over, so never cut over again. */
    data object Unreadable : PrePoolSnapshot
}

/** A `HomeLayoutSet` value is stored but cannot be decoded: it is not "nothing stored". */
internal class UndecodableHomeLayoutException : Exception("The stored home layout set cannot be decoded")

/**
 * The `HomeLayoutSet` store as the cutover needs it: the set itself and the snapshot taken when the home moved to the
 * pool. The snapshot is the marker that the home lives in the pool, and the recovery copy the migration falls back to
 * should the pool be lost; it is refreshed whenever the pool has been fully written, so it stays recent. It is never
 * the source of truth while the pool can be read.
 */
internal interface HomeLayoutCutoverStore {
    /** The stored set, null when nothing is stored; throws [UndecodableHomeLayoutException] for an unreadable value. */
    suspend fun read(): HomeLayoutSet?

    suspend fun write(layoutSet: HomeLayoutSet)

    suspend fun readPrePoolSnapshot(): PrePoolSnapshot

    /** Stores [snapshot], or removes it when null. */
    suspend fun writePrePoolSnapshot(snapshot: HomeLayoutSet?)

    /** The cutover's one write: the stripped [stored] set and the [snapshot] together (one transaction if possible). */
    suspend fun writeCutover(
        stored: HomeLayoutSet,
        snapshot: HomeLayoutSet,
    ) {
        writePrePoolSnapshot(snapshot)
        write(stored)
    }

    /** Copies an unreadable snapshot once to a quarantine key, before anything writes the snapshot key again. */
    suspend fun quarantinePrePoolSnapshot() = Unit

    /** Copies an undecodable `HomeLayoutSet` value once to a quarantine key (never over an earlier copy). */
    suspend fun quarantine() = Unit
}

/** How the home opens this process: what the shell starts from, and who renders placed items. */
internal data class HomeLayoutOpening(
    val stored: HomeLayoutSet?,
    /** True when the standard home renders from the pool ([PoolHomeLayoutRepository]); false: it is read-only. */
    val poolHome: Boolean,
    /**
     * True when the store was cut over (or cannot be read) and this process cannot use the pool: the home is shown
     * read-only from [stored] ([ReadOnlyHomeLayoutRepository]); nothing is written and no widget host id is deleted.
     */
    val readOnly: Boolean = false,
    /** Damage found at THIS start (an unreadable snapshot or set, an undecodable pool): the recovery notice. */
    val damaged: Boolean = false,
    /** Nothing was stored before this start (no `HomeLayoutSet`, no snapshot): a fresh install, for onboarding. */
    val nothingStored: Boolean = false,
) {
    /**
     * False in a read-only or damaged process: no widget host id is deleted there (the shell's removals, released ids,
     * the startup check on a discarded widget-add transaction), because what references it cannot be known for sure.
     */
    val allowsHostIdDeletion: Boolean get() = !readOnly && !damaged
}

/**
 * Opens the home at process start (once, before the first frame, off the main thread like the existing load). The
 * pool is the only home (ADR 0003): the one-time migration into it runs for everyone. The pool home only runs on a pool
 * that can be written ([CachedPoolRepository.isPersistent]); nothing here throws.
 *
 * - **The `HomeLayoutSet` store cannot be read, or its value cannot be decoded** (that value is quarantined): read-only
 *   from the snapshot when it can be read, else from defaults. Never cut over (that would replace a damaged set with
 *   defaults).
 * - **No snapshot** (first cutover): [CachedPoolRepository.initializeForCutover] resyncs and writes the pool; only once
 *   that landed are the snapshot and the stripped set written, together. Any failure: read-only from the stored set
 *   (the store was not cut over, so `HomeLayoutSet` is still the full home), nothing stripped, nothing written.
 * - **Readable snapshot**: the pool is loaded (an undecodable pool is quarantined and rebuilt from the recent
 *   snapshot). A pool that cannot be read or written: read-only from the snapshot.
 * - **Unreadable snapshot**: quarantined, never cut over again and never migrated: only a stored, readable pool that
 *   holds a home runs it; otherwise read-only from the stored set.
 */
internal suspend fun openHomeLayouts(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
): HomeLayoutOpening {
    val read = readSafely { store.read() }
    val snapshot = guarded { store.readPrePoolSnapshot() } ?: PrePoolSnapshot.Unreadable
    if (read.isFailure) {
        if (read.exceptionOrNull() is UndecodableHomeLayoutException) guarded { store.quarantine() }
        val best = (snapshot as? PrePoolSnapshot.Present)?.layoutSet
        return HomeLayoutOpening(best, poolHome = false, readOnly = true, damaged = true)
    }
    val stored = read.getOrNull()
    val opening =
        when (snapshot) {
            is PrePoolSnapshot.Present -> openPoolHome(pool, stored, snapshot.layoutSet, providerOf)
            PrePoolSnapshot.Unreadable -> openWithoutSnapshot(store, pool, stored, providerOf)
            PrePoolSnapshot.Absent -> cutOver(store, pool, stored, providerOf)
        }
    val damaged = snapshot is PrePoolSnapshot.Unreadable || pool.wasCorruptOnLoad
    val fresh = stored == null && snapshot == PrePoolSnapshot.Absent
    return if (damaged) opening.copy(damaged = true) else opening.copy(nothingStored = fresh)
}

private suspend fun openPoolHome(
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    snapshot: HomeLayoutSet,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    pool.initialize(snapshot, providerOf)
    return if (pool.current() != null && pool.isPersistent) {
        HomeLayoutOpening(stored ?: snapshot, poolHome = true)
    } else {
        readOnly(snapshot)
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
    // the resync keeps the pool (PoolCutover's hard guard) and the snapshot still gets every item. The stripped set
    // is written in the same transaction, so no start can find a snapshot beside a set with the old ids.
    val stripped =
        guarded {
            val state = if (pool.initializeForCutover(source, providerOf)) pool.current() else null
            state?.let {
                val snapshot = PoolHomeSync.projection(it, source)
                PoolHomeSync.stored(snapshot, it.pools.keys).also { set -> store.writeCutover(set, snapshot) }
            }
        }
    return if (stripped == null) readOnly(stored) else HomeLayoutOpening(stripped, true)
}

/**
 * The snapshot cannot be read, so the store was cut over but the recovery copy is gone: never cut over again (that
 * would resync from a stripped set) and never derive a pool (that would persist an empty home over what is left).
 * Only a stored, readable pool that holds a home runs it; otherwise read-only from the stored set.
 */
private suspend fun openWithoutSnapshot(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    stored: HomeLayoutSet?,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening {
    val quarantined = guarded { store.quarantinePrePoolSnapshot() } != null
    val source = stored ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    val loaded = quarantined && pool.initializeIfStored(source, providerOf)
    val state = pool.current()
    val usable = loaded && state != null && pool.isPersistent
    return if (usable && PoolCutover.holdsHome(checkNotNull(state))) {
        HomeLayoutOpening(source, poolHome = true)
    } else {
        readOnly(stored)
    }
}

/** This process shows [layoutSet] read-only and writes nothing (see [ReadOnlyHomeLayoutRepository]). */
private fun readOnly(layoutSet: HomeLayoutSet?) = HomeLayoutOpening(layoutSet, poolHome = false, readOnly = true)

/** One start's recovery notice: shown when this start found damage, unless shown since the last clean start. */
internal data class RecoveryNoticeStep(
    val show: Boolean,
    val shownAfter: Boolean,
)

/** [damaged] is this start's finding only; a clean start resets, so a later incident is announced again. */
internal fun recoveryNoticeStep(
    alreadyShown: Boolean,
    damaged: Boolean,
): RecoveryNoticeStep = RecoveryNoticeStep(show = damaged && !alreadyShown, shownAfter = damaged)

/** [block]'s result, or null when a store failed (the caller falls back; nothing is logged: no item content). */
private suspend fun <T> guarded(block: suspend () -> T): T? = readSafely(block).getOrNull()

/** [block] as a [Result]; a cancellation still propagates. */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> readSafely(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

/** Whether the one-time dock restore has run: [PoolHomePreference] on a device, a fake in tests. */
internal interface DockOverridesRestoredFlag {
    fun dockOverridesRestored(): Boolean

    fun markDockOverridesRestored()
}

/**
 * The one-time restore of the dock size and edge the workspaces held before the cut-over ([PoolDockRepair]): reads the
 * stored workspaces, applies what is still recoverable to the pool dock, and remembers it has run so a size the user
 * sets afterwards is never overwritten. Runs only when the pool home is open and writable; any failure leaves the flag
 * unset, so it is tried again next start. Nothing here throws.
 */
internal suspend fun restoreDockOverridesOnce(
    pool: CachedPoolRepository,
    preference: DockOverridesRestoredFlag,
    readWorkspaces: suspend () -> WorkspaceSet?,
) {
    if (preference.dockOverridesRestored()) return
    val workspaces = readSafely { readWorkspaces() }
    if (workspaces.isFailure) return
    val set = workspaces.getOrNull()
    val done = readSafely { pool.repairState { state -> PoolDockRepair.restoreWorkspaceOverrides(state, set) } }
    if (done.getOrDefault(false)) preference.markDockOverridesRestored()
}

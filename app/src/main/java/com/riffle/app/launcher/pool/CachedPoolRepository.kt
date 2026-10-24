package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolCutover
import com.riffle.core.domain.launcher.workspace.pool.PoolLayoutFold
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/** Suspend storage for the [PoolStoreState]; the DataStore-backed store implements it, tests fake it. */
internal interface PoolStorePort {
    /** What is stored, null when nothing is; throws [UndecodablePoolException] when a stored value is unreadable. */
    suspend fun read(): PoolStoreState?

    suspend fun write(state: PoolStoreState)

    /**
     * Copies the stored (undecodable) value once to a quarantine key, so a recovery may write the pool key without
     * losing it; never over an earlier copy. Throws when the copy could not be made.
     */
    suspend fun quarantine() = Unit

    /** True when a quarantined copy exists (it is never overwritten; a backup import is how to recover from it). */
    suspend fun hasQuarantine(): Boolean = false
}

/** A pool value is stored but cannot be decoded (corrupt, truncated, or a future schema). */
internal class UndecodablePoolException : Exception("The stored pool cannot be decoded")

/**
 * The synchronous view of the placed-items pool that the Workspaces preview reads, over a suspend
 * [PoolStorePort]. [pool] is null until [initialize] has run, so before then (and while the preview is off,
 * because nothing calls [initialize]) nothing is read, migrated or written.
 *
 * [initialize] reads what is stored and runs [PoolCutover.ensureMigrated], the one-time, flag-guarded import
 * from `HomeLayoutSet` (which is only read, never changed), then persists the result only if it changed. A
 * second call does nothing; a later process finds the flag and does not migrate again. A read that throws
 * disables persistence for this process, so a transient storage failure can never replace a pool that exists on
 * disk. A blob that cannot be decoded is copied once to a quarantine key and then reads as nothing stored and is
 * re-derived (the standard home re-derives it from its recovery snapshot, see `openHomeLayouts`).
 *
 * [reimport] replaces the pools with a fresh import of the current `HomeLayoutSet`. Until pool editing exists
 * the standard home is still edited in `HomeLayoutSet`, so this is how the preview catches up with it.
 *
 * Pools are keyed by the Workspaces layout class (N10): a landscape phone reads and edits the phone pool, and a
 * stored or restored landscape pool is folded into it once ([PoolLayoutFold]).
 *
 * Nothing here stores or logs item content: the pool holds apps, folders and widget providers only.
 */
@Suppress("TooManyFunctions") // Load, cutover, edit, flush and backup paths of one small store.
internal class CachedPoolRepository(
    private val store: PoolStorePort,
) {
    @Volatile
    private var cached: PoolStoreState? = null

    @Volatile
    private var persistenceEnabled = true

    @Volatile
    private var corruptOnLoad = false
    private val writtenListeners = CopyOnWriteArrayList<() -> Unit>()

    /** True while [cached] holds a change that has not been written yet. Guarded by [stateLock]. */
    private var dirty = false
    private val stateLock = Any()
    private val lock = Mutex()
    private val mutableVersion = MutableStateFlow(0)

    /** Increases every time [pool] starts returning something different. */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    suspend fun initialize(
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ) = lock.withLock {
        if (cached == null) {
            val read = readStored()
            val stored = read?.let(PoolLayoutFold::fold)
            val result = PoolCutover.ensureMigrated(stored, layoutSet, providerOf)
            publish(result.state)
            if (result.changed || stored != read) persist(result.state)
        }
    }

    /**
     * [initialize] for the standard home's cutover (S4): the same read and storage failure policy, but the stored pool
     * is brought in line with [layoutSet] first ([PoolCutover.resync]: the migrated workspaces' arrangements and the
     * docks are imported afresh from [layoutSet], everything else stored is kept), because until now the standard home
     * was edited in `HomeLayoutSet` and a pool imported earlier is stale. Persists the result when it changed.
     * Returns true only when the result is on disk (written now, or equal to what was read), so the caller may stop
     * writing placed items to `HomeLayoutSet`; false after a read or write failure, or when the pool was already
     * loaded (then nothing is done).
     */
    suspend fun initializeForCutover(
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): Boolean =
        lock.withLock {
            if (cached != null) return@withLock false
            val read = readStored()
            val result = PoolCutover.resync(read, layoutSet, providerOf)
            publish(result.state)
            when {
                !persistenceEnabled -> false
                result.state == read -> true
                else -> persist(result.state)
            }
        }

    /**
     * [initialize] that never derives a pool from [layoutSet]: only a pool that is stored and readable is loaded (and
     * gets an owed dock import from [layoutSet]). Absent, undecodable (quarantined, never overwritten) or unreadable:
     * nothing is published or written and false is returned. For a start where [layoutSet] may be stripped and there
     * is no recovery copy to migrate from.
     */
    suspend fun initializeIfStored(
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ): Boolean =
        lock.withLock {
            if (cached != null) return@withLock true
            val read = readStored() ?: return@withLock false
            val stored = PoolLayoutFold.fold(read)
            val result = PoolCutover.ensureMigrated(stored, layoutSet, providerOf)
            publish(result.state)
            if (result.changed || stored != read) persist(result.state)
            true
        }

    /** Whether the store keeps a quarantined (undecodable) copy; false when that cannot be read. */
    suspend fun hasQuarantine(): Boolean =
        runCatching { store.hasQuarantine() }.onFailure { if (it is CancellationException) throw it }.getOrDefault(
            false,
        )

    /** False once a read failed: nothing will be written this process, so nothing may depend on a write landing. */
    val isPersistent: Boolean
        get() = persistenceEnabled

    /** True when this process found a stored pool it could not decode (it was quarantined, then re-derived). */
    val wasCorruptOnLoad: Boolean
        get() = corruptOnLoad

    /** True when nothing in memory is waiting to be written (what is on disk is what [current] returns). */
    val isWritten: Boolean
        get() = synchronized(stateLock) { !dirty }

    /** Called (on the writing thread) after every write that landed, whoever asked for it. */
    fun onWritten(listener: () -> Unit) {
        writtenListeners += listener
    }

    /**
     * The stored state, or null when nothing usable is stored. An undecodable value is quarantined first (so a
     * recovery may write over the key); if that copy fails, or the read itself fails, persistence is disabled for the
     * process, so what is on disk is never replaced.
     */
    private suspend fun readStored(): PoolStoreState? {
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        val failure = read.exceptionOrNull()
        if (failure is UndecodablePoolException) {
            corruptOnLoad = true
            val copied = runCatching { store.quarantine() }.onFailure { if (it is CancellationException) throw it }
            if (copied.isFailure) persistenceEnabled = false
        } else if (failure != null) {
            persistenceEnabled = false
        }
        return read.getOrNull()
    }

    /** Replaces the pools by a fresh import of [layoutSet]. Does nothing before [initialize]. */
    suspend fun reimport(
        layoutSet: HomeLayoutSet,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    ) = lock.withLock {
        if (cached != null) {
            val result = PoolCutover.reimport(layoutSet, providerOf)
            publish(result.state)
            persist(result.state)
        }
    }

    /**
     * Replaces the pool of [deviceClass] by an edited value (pool editing). In memory and synchronous, so a rapid
     * sequence of edits stays ordered; the write is [flush]'s, which the caller debounces. Returns false and changes
     * nothing before [initialize], so an edit can never create state out of nothing.
     */
    fun update(
        deviceClass: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean =
        synchronized(stateLock) {
            val current = cached
            if (current != null) {
                cached = current.copy(pools = current.pools + (deviceClass.workspaceLayoutClass() to pool))
                dirty = true
            }
            current != null
        }.also { updated -> if (updated) mutableVersion.update { it + 1 } }

    /**
     * Replaces the whole in-memory state (the standard home's edits can touch several layouts at once), like [update]:
     * synchronous, written by [flush], false and nothing changed before [initialize]. The import flags are kept.
     */
    fun replace(state: PoolStoreState): Boolean {
        var changed = false
        val loaded =
            synchronized(stateLock) {
                val current = cached
                if (current != null && current.pools != state.pools) {
                    cached = current.copy(pools = state.pools)
                    dirty = true
                    changed = true
                }
                current != null
            }
        if (changed) mutableVersion.update { it + 1 }
        return loaded
    }

    /**
     * Writes the latest state if an [update] has not been written yet. Atomic (one DataStore transaction), a failed
     * write is tolerated and keeps the change pending for the next flush, and nothing is written once a read failure
     * disabled persistence, so what is on disk is never replaced by a guess. Returns true when nothing is left
     * unwritten (so a caller may now act on what the pool dropped, such as deleting widget host ids).
     */
    suspend fun flush(): Boolean =
        lock.withLock {
            val snapshot = synchronized(stateLock) { cached.takeIf { dirty } }
            when {
                snapshot == null -> true
                !persistenceEnabled -> false
                else -> {
                    val written =
                        runCatching { store.write(snapshot) }.onFailure { if (it is CancellationException) throw it }
                    if (written.isSuccess) synchronized(stateLock) { if (cached === snapshot) dirty = false }
                    if (written.isSuccess) writtenListeners.forEach { it() }
                    written.isSuccess && synchronized(stateLock) { !dirty }
                }
            }
        }

    /** The in-memory state; null before [initialize]. */
    fun current(): PoolStoreState? = cached

    /** The in-memory state once [initialize] has run, else what is stored (null when nothing is). For backup export. */
    suspend fun currentOrStored(): PoolStoreState? = cached ?: store.read()

    /**
     * Replaces the pool from a backup. Before [initialize] the value is only written, so the next start reads it (and,
     * as the backup kept the import flag, does not import over it); afterwards the in-memory copy is replaced too and
     * observers refresh, and a failed write stays pending for the next [flush]. A write may throw before [initialize];
     * the backup flow tolerates that.
     */
    suspend fun replaceFromBackup(state: PoolStoreState) =
        lock.withLock {
            val folded = PoolLayoutFold.fold(state)
            if (cached != null) {
                synchronized(stateLock) {
                    cached = folded
                    dirty = true
                }
                mutableVersion.update { it + 1 }
                persist(folded)
            } else if (persistenceEnabled) {
                store.write(folded)
            }
        }

    /** The pool of [deviceClass]; null before [initialize] or when that class has none. */
    fun pool(deviceClass: HomeLayoutDeviceClass): PlacedItemPool? = cached?.poolFor(deviceClass.workspaceLayoutClass())

    private fun publish(state: PoolStoreState) {
        synchronized(stateLock) { cached = state }
        mutableVersion.update { it + 1 }
    }

    /** Writes [state]; true when it landed. A failure is tolerated (the next [flush] of an edit writes again). */
    private suspend fun persist(state: PoolStoreState): Boolean {
        if (!persistenceEnabled) return false
        val written = runCatching { store.write(state) }.onFailure { if (it is CancellationException) throw it }
        if (written.isSuccess) synchronized(stateLock) { if (cached === state) dirty = false }
        if (written.isSuccess) writtenListeners.forEach { it() }
        return written.isSuccess
    }
}

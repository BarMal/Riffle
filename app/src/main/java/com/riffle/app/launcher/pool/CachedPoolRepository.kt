package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolCutover
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suspend storage for the [PoolStoreState]; the DataStore-backed store implements it, tests fake it. */
internal interface PoolStorePort {
    suspend fun read(): PoolStoreState?

    suspend fun write(state: PoolStoreState)
}

/**
 * The synchronous view of the placed-items pool that the Workspaces preview reads, over a suspend
 * [PoolStorePort]. [pool] is null until [initialize] has run, so before then (and while the preview is off,
 * because nothing calls [initialize]) nothing is read, migrated or written.
 *
 * [initialize] reads what is stored and runs [PoolCutover.ensureMigrated], the one-time, flag-guarded import
 * from `HomeLayoutSet` (which is only read, never changed), then persists the result only if it changed. A
 * second call does nothing; a later process finds the flag and does not migrate again. A read that throws
 * (as opposed to a blob that cannot be decoded, which reads as nothing stored and is re-derived) disables
 * persistence for this process, so a transient storage failure can never replace a pool that exists on disk.
 *
 * [reimport] replaces the pools with a fresh import of the current `HomeLayoutSet`. Until pool editing exists
 * the standard home is still edited in `HomeLayoutSet`, so this is how the preview catches up with it.
 *
 * Nothing here stores or logs item content: the pool holds apps, folders and widget providers only.
 */
internal class CachedPoolRepository(
    private val store: PoolStorePort,
) {
    @Volatile
    private var cached: PoolStoreState? = null

    @Volatile
    private var persistenceEnabled = true

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
            val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
            if (read.isFailure) persistenceEnabled = false
            val stored = read.getOrNull()
            val result = PoolCutover.ensureMigrated(stored, layoutSet, providerOf)
            publish(result.state)
            if (result.changed) persist(result.state)
        }
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
                cached = current.copy(pools = current.pools + (deviceClass to pool))
                dirty = true
            }
            current != null
        }.also { updated -> if (updated) mutableVersion.update { it + 1 } }

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
                    written.isSuccess && synchronized(stateLock) { !dirty }
                }
            }
        }

    /** The pool of [deviceClass]; null before [initialize] or when that class has none. */
    fun pool(deviceClass: HomeLayoutDeviceClass): PlacedItemPool? = cached?.poolFor(deviceClass)

    private fun publish(state: PoolStoreState) {
        synchronized(stateLock) { cached = state }
        mutableVersion.update { it + 1 }
    }

    private suspend fun persist(state: PoolStoreState) {
        if (persistenceEnabled) {
            val written = runCatching { store.write(state) }.onFailure { if (it is CancellationException) throw it }
            if (written.isSuccess) synchronized(stateLock) { if (cached === state) dirty = false }
        }
    }
}

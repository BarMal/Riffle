package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suspend storage for the [WorkspaceSet]; the DataStore-backed store implements it, tests fake it. */
interface WorkspaceStorePort {
    suspend fun read(): WorkspaceSet?

    suspend fun write(set: WorkspaceSet)
}

/**
 * The synchronous [WorkspaceRepository] the shell reads, over a suspend [WorkspaceStorePort]: the one
 * explicit wiring point between the workspace store and the UI.
 *
 * [load] reads an in-memory copy and is null until [initialize] has run, so before then (and when the
 * workspace system is switched off, because nothing calls [initialize]) every consumer sees "no
 * workspaces". [initialize] reads what is stored and fills only the gaps through
 * [WorkspaceMigration.ensureMigrated], so stored workspaces are never overwritten; it writes nothing
 * itself, because the migration is deterministic and is re-derived on the next start until the first
 * [save]. [save] updates the copy at once and persists in order on [scope].
 *
 * A read that throws (as opposed to a blob that cannot be decoded, which reads as null) falls back to the
 * in-memory default and disables persistence for this process, so a transient storage failure can never
 * replace workspaces that exist on disk. [version] changes whenever the copy does, for UI that observes it.
 */
class CachedWorkspaceRepository(
    private val store: WorkspaceStorePort,
    private val scope: CoroutineScope,
) : WorkspaceRepository {
    @Volatile
    private var cached: WorkspaceSet? = null
    private val writeLock = Mutex()
    private val mutableVersion = MutableStateFlow(0)

    @Volatile
    private var persistenceEnabled = true

    /** Increases every time [load] starts returning something different. */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    suspend fun initialize(layoutSet: HomeLayoutSet) =
        initialize { stored -> WorkspaceMigration.ensureMigrated(stored, layoutSet) }

    /** Like [initialize], but [bootstrap] decides what to hold given what is stored (null when nothing is). */
    suspend fun initialize(bootstrap: (WorkspaceSet?) -> WorkspaceSet) {
        if (cached != null) return
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        if (read.isFailure) persistenceEnabled = false
        val loaded = bootstrap(read.getOrNull())
        // A save that raced the read wins: it is newer than anything the store held.
        if (cached == null) {
            cached = loaded
            mutableVersion.update { it + 1 }
        }
    }

    override fun load(): WorkspaceSet? = cached

    /** The in-memory set, else what is stored (for the backup flow); null when none. */
    suspend fun currentOrStored(): WorkspaceSet? = cached ?: store.read()

    /**
     * Replaces the set from a backup. While the workspace system has not initialised this process the value
     * is only written to the store (so the next [initialize] reads it and fills any gaps); afterwards it is a
     * normal [save], so observers refresh.
     */
    fun replaceFromBackup(set: WorkspaceSet) {
        if (cached != null) {
            save(set)
        } else if (persistenceEnabled) {
            scope.launch { writeLock.withLock { store.write(set) } }
        }
    }

    override fun save(set: WorkspaceSet) {
        cached = set
        mutableVersion.update { it + 1 }
        if (persistenceEnabled) scope.launch { writeLock.withLock { store.write(cached ?: set) } }
    }
}

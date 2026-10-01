package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CoroutineScope
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
 */
class CachedWorkspaceRepository(
    private val store: WorkspaceStorePort,
    private val scope: CoroutineScope,
) : WorkspaceRepository {
    @Volatile
    private var cached: WorkspaceSet? = null
    private val writeLock = Mutex()

    suspend fun initialize(layoutSet: HomeLayoutSet) {
        if (cached != null) return
        val loaded = WorkspaceMigration.ensureMigrated(store.read(), layoutSet)
        // A save that raced the read wins: it is newer than anything the store held.
        if (cached == null) cached = loaded
    }

    override fun load(): WorkspaceSet? = cached

    override fun save(set: WorkspaceSet) {
        cached = set
        scope.launch { writeLock.withLock { store.write(cached ?: set) } }
    }
}

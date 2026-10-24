package com.riffle.app.launcher

import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Backs the backup flow with the workspace, exclusion and pool repositories, never blocking a thread.
 *
 * Export reads the in-memory copy when the workspace system has initialised this process; otherwise it uses
 * what [prepareExport] loaded from the stores on [scope] while the file picker was open (null when nothing is
 * stored, so with the workspace preview never used the backup is unchanged). Restores write to the stores on
 * [scope] and update the in-memory copies when they exist.
 */
internal class AndroidWorkspaceBackupPort(
    private val workspaces: CachedWorkspaceRepository,
    private val exclusions: CachedExclusionRepository,
    private val pool: CachedPoolRepository,
    private val scope: CoroutineScope,
) : WorkspaceBackupPort {
    @Volatile
    private var storedSet: WorkspaceSet? = null

    @Volatile
    private var storedRules: LayoutExclusionRules? = null

    @Volatile
    private var storedPool: PoolStoreState? = null

    override fun prepareExport() {
        storedSet = null
        storedRules = null
        storedPool = null
        scope.launch {
            runCatching { workspaces.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedSet = it }
            runCatching { exclusions.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedRules = it }
            runCatching { pool.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedPool = it }
        }
    }

    override fun currentWorkspaceSet(): WorkspaceSet? = workspaces.load() ?: storedSet

    override fun currentExclusions(): LayoutExclusionRules? = storedRules

    override fun currentPool(): PoolStoreState? = pool.current() ?: storedPool

    override fun restoreWorkspaceSet(set: WorkspaceSet) = workspaces.replaceFromBackup(set)

    override fun restoreExclusions(rules: LayoutExclusionRules) {
        scope.launch {
            runCatching { exclusions.replaceFromBackup(rules) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    override fun restorePool(state: PoolStoreState) {
        scope.launch {
            runCatching { pool.replaceFromBackup(state) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }
}

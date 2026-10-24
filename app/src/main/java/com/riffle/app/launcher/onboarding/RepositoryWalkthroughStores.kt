package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [WalkthroughStores] over the process's workspace and pool repositories (Android-free). The pool change is made in
 * memory at once ([CachedPoolRepository.update]: the home sees it as an external change and re-projects) and written
 * by [flushPool] on [scope] (the pool-backed home's flush, which also releases host ids and refreshes the recovery
 * snapshot); the workspace save persists in order on the repository's own scope.
 */
internal class RepositoryWalkthroughStores(
    private val workspaces: CachedWorkspaceRepository,
    private val pool: CachedPoolRepository,
    private val scope: CoroutineScope,
    /** True in a read-only process (`HomeLayoutRuntime.readOnlyHome != null`). */
    private val isReadOnly: () -> Boolean,
    /** True while the pool-backed home holds item edits (`PoolHomeLayoutRepository.awaitingWorkspaces`). */
    private val holdsEdits: () -> Boolean,
    private val flushPool: suspend () -> Unit = { pool.flush() },
) : WalkthroughStores {
    override val readOnly: Boolean get() = isReadOnly()

    override val loading: Boolean get() = workspaces.load() == null || holdsEdits()

    override fun workspaces(): WorkspaceSet? = workspaces.load()

    override fun saveWorkspaces(set: WorkspaceSet) {
        if (!readOnly) workspaces.save(set)
    }

    override fun pool(layoutClass: HomeLayoutDeviceClass): PlacedItemPool? = pool.pool(layoutClass)

    override fun savePool(
        layoutClass: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean {
        if (readOnly) return false
        val updated = this.pool.update(layoutClass, pool)
        if (updated) scope.launch { flushPool() }
        return updated
    }
}

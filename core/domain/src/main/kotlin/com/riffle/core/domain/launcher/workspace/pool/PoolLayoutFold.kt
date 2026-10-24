package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutFold
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * Folds a [PoolStoreState] stored before owner decision N10 into the Workspaces layout classes
 * ([workspaceLayoutClass]): a `PHONE_LANDSCAPE` pool becomes the `PHONE` pool when that has none, else it is
 * dropped (`PHONE` wins). Applied once on load and on backup restore. Pure and idempotent; never throws; the import
 * flag is kept as stored.
 */
object PoolLayoutFold {
    fun fold(state: PoolStoreState): PoolStoreState =
        if (WorkspaceLayoutFold.needsFold(state.pools.keys)) {
            state.copy(pools = WorkspaceLayoutFold.foldMap(state.pools))
        } else {
            state
        }
}

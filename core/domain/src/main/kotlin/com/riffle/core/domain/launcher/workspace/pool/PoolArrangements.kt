package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/** Per-workspace arrangements: giving a workspace its own placed pages. */
object PoolArrangements {
    /**
     * Fork on first edit: gives [workspaceId] its own arrangement, copied from the one it currently borrows (the first
     * of [candidates] with pages, as [PoolHomeView.arrangementOf] resolves it; pass `PoolHomeView.candidates(...)`).
     * Apps and widget instances are shared by reference (one host id, one configuration), folders are cloned (the
     * fork's folders are independent), pages are copied with their ids, grids and pinned flags, and the new-app setting
     * is copied. The dock is not touched (it is the layout's, not a workspace's). The fork records where it came from
     * ([Arrangement.forkedFrom]: the source's own origin when the source is itself a fork).
     *
     * Idempotent: a workspace that already has an arrangement is returned unchanged (`Done` with the same pool).
     * Refused with [PoolRejection.NOTHING_TO_FORK] when no candidate other than [workspaceId] has pages.
     */
    fun fork(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        candidates: List<WorkspaceId>,
        ids: WorkspaceIdFactory,
    ): PoolResult {
        if (workspaceId in pool.arrangements) return PoolResult.Done(PoolEdit(pool))
        val source = PoolHomeView.arrangementOf(pool, candidates.filter { it != workspaceId })
        return if (source == null) {
            rejected(PoolRejection.NOTHING_TO_FORK)
        } else {
            PoolCopy.copyArrangement(pool, source, workspaceId, ids, CopyMode.FORK)
        }
    }

    /** Whether [workspaceId] has an arrangement of its own (otherwise it borrows one and an edit must [fork] first). */
    fun ownsArrangement(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
    ): Boolean = workspaceId in pool.arrangements
}

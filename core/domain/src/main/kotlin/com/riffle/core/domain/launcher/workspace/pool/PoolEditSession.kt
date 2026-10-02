package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HostedWidgetId

/**
 * What one [PoolEditSession.apply] did: the next session, the operation's own [result] (which carries the rejection
 * reason), and whether the pool [changed]. A rejected or no-op edit leaves the session as it was.
 */
data class PoolSessionStep(
    val session: PoolEditSession,
    val result: PoolResult,
    val changed: Boolean,
)

/** One applied edit: the pool value before and after it, and the widget host ids it released. */
data class PoolHistoryEntry(
    val before: PlacedItemPool,
    val after: PlacedItemPool,
    val releasedHostIds: Set<HostedWidgetId>,
)

/**
 * Undo and redo for pool editing, following `WorkspaceEditSession`: every edit is one atomic, immutable
 * [PoolEdit], so Undo is "restore the previous pool value" and Redo is "restore the next one". A rejected or
 * no-op edit leaves the session untouched. History is bounded by [MAX_HISTORY].
 *
 * The session also owns the deferred host-id release. Ids that left the pool wait in [queue] until the Undo
 * window closes ([finish]); an Undo forgets ids that are live again ([HostIdDeletionQueue.withoutReferenced]) and a
 * Redo queues them again. The queue never names a widget the pool still holds.
 */
data class PoolEditSession(
    val pool: PlacedItemPool,
    val queue: HostIdDeletionQueue = HostIdDeletionQueue(),
    private val undoStack: List<PoolHistoryEntry> = emptyList(),
    private val redoStack: List<PoolHistoryEntry> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Applies one operation's result. */
    fun apply(result: PoolResult): PoolSessionStep =
        when {
            result !is PoolResult.Done -> PoolSessionStep(this, result, changed = false)
            result.edit.pool == pool -> PoolSessionStep(this, result, changed = false)
            else -> {
                val entry = PoolHistoryEntry(pool, result.edit.pool, result.edit.releasedHostIds)
                val next =
                    copy(
                        pool = result.edit.pool,
                        queue = queue.plus(result.edit),
                        undoStack = (undoStack + entry).takeLast(MAX_HISTORY),
                        redoStack = emptyList(),
                    )
                PoolSessionStep(next, result, changed = true)
            }
        }

    fun undo(): PoolEditSession =
        undoStack.lastOrNull()?.let { entry ->
            copy(
                pool = entry.before,
                queue = queue.withoutReferenced(entry.before),
                undoStack = undoStack.dropLast(1),
                redoStack = redoStack + entry,
            )
        } ?: this

    fun redo(): PoolEditSession =
        redoStack.lastOrNull()?.let { entry ->
            copy(
                pool = entry.after,
                queue = HostIdDeletionQueue(queue.pending + entry.releasedHostIds).withoutReferenced(entry.after),
                undoStack = undoStack + entry,
                redoStack = redoStack.dropLast(1),
            )
        } ?: this

    /**
     * Closes the Undo window: the host ids to delete now (none that a live widget still holds) and a session with
     * the same pool and no history.
     */
    fun finish(): Pair<Set<HostedWidgetId>, PoolEditSession> {
        val (ids, empty) = queue.withoutReferenced(pool).drain()
        return ids to PoolEditSession(pool, empty)
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}

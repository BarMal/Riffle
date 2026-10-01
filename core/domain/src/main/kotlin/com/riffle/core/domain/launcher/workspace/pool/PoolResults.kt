package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** Where one reference sits. */
data class ReferenceSite(
    val workspaceId: WorkspaceId,
    val pageId: LauncherPageId,
)

/** Derived reference counts. Nothing here is stored, so a counter cannot drift from the arrangements. */
object PoolReferences {
    /** Every reference of every referenced item, in arrangement then page then placement order. */
    fun index(pool: PlacedItemPool): Map<PoolItemId, List<ReferenceSite>> =
        buildMap<PoolItemId, MutableList<ReferenceSite>> {
            pool.arrangements.forEach { (workspaceId, arrangement) ->
                arrangement.pages.forEach { page ->
                    page.placements.forEach {
                        getOrPut(it.item) { mutableListOf() } += ReferenceSite(workspaceId, page.id)
                    }
                }
            }
        }

    fun count(
        pool: PlacedItemPool,
        id: PoolItemId,
    ): Int = index(pool)[id]?.size ?: 0

    /** Items with no reference: what garbage collection removes. */
    fun unreferenced(pool: PlacedItemPool): Set<PoolItemId> = pool.items.keys - index(pool).keys

    fun referencedIn(arrangement: Arrangement): Set<PoolItemId> =
        arrangement.pages.flatMap { page -> page.placements.map { it.item } }.toSet()

    /** Host ids still held by a bound widget of [pool]. */
    fun hostIds(pool: PlacedItemPool): Set<HostedWidgetId> =
        pool.items.values.mapNotNull { (it as? PoolWidget)?.hostedId }.toSet()
}

/**
 * The outcome of a successful operation. [removedItems] and [releasedHostIds] are what Undo and the app layer
 * need: restoring the previous [PlacedItemPool] value is exact, and [releasedHostIds] are queued for
 * `deleteHostedWidgetId` only after the Undo window closes ([HostIdDeletionQueue]).
 */
data class PoolEdit(
    val pool: PlacedItemPool,
    val removedItems: List<PoolItem> = emptyList(),
    val createdItemIds: List<PoolItemId> = emptyList(),
    val releasedHostIds: Set<HostedWidgetId> = emptySet(),
)

enum class PoolRejection {
    UNKNOWN_WORKSPACE,
    UNKNOWN_PAGE,
    UNKNOWN_ITEM,
    NOT_PLACED_HERE,
    ALREADY_IN_ARRANGEMENT,
    WIDGET_ALREADY_PLACED,
    FOLDER_SHARING_IS_EXPLICIT,
    NOT_A_FOLDER,
    NOT_A_WIDGET,
    PROVIDER_UNKNOWN,
    ITEM_ID_IN_USE,
    HOST_ID_IN_USE,
    WIDGET_ALREADY_BOUND,
    WORKSPACE_EXISTS,
    SAME_WORKSPACE,
    OUT_OF_BOUNDS,
    COLLISION,
    NO_AVAILABLE_CELL,
    INVALID_PLACEMENT,
}

sealed interface PoolResult {
    data class Done(val edit: PoolEdit) : PoolResult

    data class Rejected(val reason: PoolRejection) : PoolResult
}

internal fun rejected(reason: PoolRejection): PoolResult = PoolResult.Rejected(reason)

internal fun PlacementRejectionReason.toPoolRejection(): PoolRejection =
    when (this) {
        PlacementRejectionReason.OUT_OF_BOUNDS -> PoolRejection.OUT_OF_BOUNDS
        PlacementRejectionReason.COLLISION -> PoolRejection.COLLISION
        PlacementRejectionReason.NO_AVAILABLE_CELL -> PoolRejection.NO_AVAILABLE_CELL
        PlacementRejectionReason.ITEM_NOT_FOUND -> PoolRejection.NOT_PLACED_HERE
        PlacementRejectionReason.DUPLICATE_ITEM_ID -> PoolRejection.ALREADY_IN_ARRANGEMENT
        else -> PoolRejection.INVALID_PLACEMENT
    }

/** The pool after a successful result, or [fallback] unchanged when it was rejected. */
fun PoolResult.poolOr(fallback: PlacedItemPool): PlacedItemPool = (this as? PoolResult.Done)?.edit?.pool ?: fallback

/** Garbage collection and the diff that turns two pool values into a [PoolEdit]. */
object PoolGc {
    /** [pool] without zero-reference items. Folder entries go with their folder. */
    fun collect(pool: PlacedItemPool): PlacedItemPool {
        val referenced = PoolReferences.index(pool).keys
        return pool.copy(items = pool.items.filterKeys { it in referenced })
    }

    /**
     * Collects [after] and describes the change from [before]. A collected widget's host id is released
     * unless a surviving widget still holds the same id.
     */
    fun finish(
        before: PlacedItemPool,
        after: PlacedItemPool,
    ): PoolEdit {
        val collected = collect(after)
        val removed = before.items.filterKeys { it !in collected.items }.values.toList()
        val held = PoolReferences.hostIds(collected)
        val released = removed.mapNotNull { (it as? PoolWidget)?.hostedId }.filterNot { it in held }.toSet()
        return PoolEdit(
            pool = collected,
            removedItems = removed,
            createdItemIds = collected.items.keys.filter { it !in before.items },
            releasedHostIds = released,
        )
    }
}

/**
 * Host ids waiting to be deleted. Pure: the app layer persists it, calls `deleteHostedWidgetId` for
 * [drain]'s result after the Undo window, and calls [withoutReferenced] after an Undo so a restored widget's
 * id is not deleted.
 */
data class HostIdDeletionQueue(val pending: Set<HostedWidgetId> = emptySet()) {
    fun plus(edit: PoolEdit): HostIdDeletionQueue = HostIdDeletionQueue(pending + edit.releasedHostIds)

    fun withoutReferenced(pool: PlacedItemPool): HostIdDeletionQueue =
        HostIdDeletionQueue(pending - PoolReferences.hostIds(pool))

    /** The ids to delete now and the emptied queue. */
    fun drain(): Pair<Set<HostedWidgetId>, HostIdDeletionQueue> = pending to HostIdDeletionQueue()
}

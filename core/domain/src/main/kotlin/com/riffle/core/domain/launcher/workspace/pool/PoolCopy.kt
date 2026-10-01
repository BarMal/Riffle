package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

enum class CopyMode {
    /** Apps and folders are cloned: the copy is independent of the original (the default). */
    CLONE,

    /** Apps and folders are referenced, not cloned ("Keep items shared"). Widgets are never shared. */
    KEEP_SHARED,
}

/**
 * Copy semantics: copies clone, sharing is an explicit choice. Widgets are never cloned live: a copy gets an
 * unbound placeholder (same cell, span and provider, `hostedId = null`) that the app layer offers to set up.
 */
object PoolCopy {
    /** Replaces [to]'s arrangement by a copy of [from]'s (creating it when absent). */
    fun copyArrangement(
        pool: PlacedItemPool,
        from: WorkspaceId,
        to: WorkspaceId,
        ids: WorkspaceIdFactory,
        mode: CopyMode = CopyMode.CLONE,
    ): PoolResult {
        val source = pool.arrangements[from]
        return when {
            source == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            from == to -> rejected(PoolRejection.SAME_WORKSPACE)
            else -> {
                val taken = pool.items.keys.toMutableSet()
                val map = LinkedHashMap<PoolItemId, PoolItemId>()
                PoolReferences.referencedIn(source).filter { it in pool.items }.forEach { old ->
                    val keep = mode == CopyMode.KEEP_SHARED && pool.items[old] !is PoolWidget
                    map[old] = if (keep) old else PoolIds.fresh(taken, ids).also { taken += it }
                }
                val clones =
                    map.filter {
                            (old, new) ->
                        old != new
                    }.mapNotNull { (old, new) -> pool.items[old]?.let { clone(it, new) } }
                val copy = remap(source) { map[it] }
                PoolResult.Done(
                    PoolGc.finish(
                        pool,
                        pool.copy(
                            items = pool.items + clones.associateBy { it.id },
                            arrangements = pool.arrangements + (to to copy),
                        ),
                    ),
                )
            }
        }
    }

    /** "Duplicate workspace": [copyArrangement] into a workspace id that has no arrangement yet. */
    fun duplicateWorkspace(
        pool: PlacedItemPool,
        from: WorkspaceId,
        newId: WorkspaceId,
        ids: WorkspaceIdFactory,
        mode: CopyMode = CopyMode.CLONE,
    ): PoolResult =
        if (newId in pool.arrangements) {
            rejected(
                PoolRejection.WORKSPACE_EXISTS,
            )
        } else {
            copyArrangement(pool, from, newId, ids, mode)
        }

    /**
     * "Copy from other layout": [target]'s pool is replaced by clones of [source]'s arrangements for the workspaces in
     * [workspaceIdMap] (old id to new id), items cloned with fresh ids, widgets as placeholders. Items that two copied
     * workspaces shared stay shared in the copy. Replaced widgets' host ids are released (deferred).
     */
    fun copyFromOtherLayout(
        source: PlacedItemPool,
        target: PlacedItemPool,
        workspaceIdMap: Map<WorkspaceId, WorkspaceId>,
        ids: WorkspaceIdFactory,
    ): PoolEdit {
        val taken = target.items.keys.toMutableSet()
        val map = LinkedHashMap<PoolItemId, PoolItemId>()
        val arrangements =
            workspaceIdMap.mapNotNull { (old, new) ->
                source.arrangements[old]?.let { arrangement ->
                    new to
                        remap(arrangement) { id ->
                            id.takeIf { it in source.items }?.let {
                                map.getOrPut(it) {
                                    PoolIds.fresh(taken, ids).also {
                                            fresh ->
                                        taken += fresh
                                    }
                                }
                            }
                        }
                }
            }.toMap()
        val items = map.mapNotNull { (old, new) -> source.items[old]?.let { clone(it, new) } }.associateBy { it.id }
        return PoolGc.finish(target, PlacedItemPool(items, arrangements))
    }

    /** "Make independent": gives [workspaceId] its own clone of a folder it shares. A no-op for an unshared folder. */
    fun makeFolderIndependent(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        folderId: PoolItemId,
        ids: WorkspaceIdFactory,
    ): PoolResult {
        val folder = pool.items[folderId]
        val arrangement = pool.arrangements[workspaceId]
        return when {
            arrangement == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            folder == null -> rejected(PoolRejection.UNKNOWN_ITEM)
            folder !is PoolFolder -> rejected(PoolRejection.NOT_A_FOLDER)
            folderId !in PoolReferences.referencedIn(arrangement) -> rejected(PoolRejection.NOT_PLACED_HERE)
            PoolReferences.count(pool, folderId) == 1 -> PoolResult.Done(PoolEdit(pool))
            else -> {
                val fresh = PoolIds.fresh(pool.items.keys, ids)
                val own = remap(arrangement) { if (it == folderId) fresh else it }
                PoolResult.Done(
                    PoolGc.finish(
                        pool,
                        pool.copy(
                            items = pool.items + (fresh to folder.copy(id = fresh)),
                            arrangements = pool.arrangements + (workspaceId to own),
                        ),
                    ),
                )
            }
        }
    }

    private fun clone(
        item: PoolItem,
        id: PoolItemId,
    ): PoolItem =
        when (item) {
            is PoolApp -> item.copy(id = id)
            is PoolFolder -> item.copy(id = id)
            is PoolWidget -> item.copy(id = id, hostedId = null)
        }

    /** The arrangement with every reference rewritten by [map]; a reference mapped to null is dropped. */
    private fun remap(
        arrangement: Arrangement,
        map: (PoolItemId) -> PoolItemId?,
    ): Arrangement =
        arrangement.copy(
            pages =
                arrangement.pages.map { page ->
                    page.copy(placements = page.placements.mapNotNull { p -> map(p.item)?.let { p.copy(item = it) } })
                },
        )
}

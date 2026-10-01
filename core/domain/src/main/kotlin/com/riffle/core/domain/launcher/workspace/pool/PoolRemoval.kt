package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** What deleting a workspace's arrangement would remove, for the confirmation text. */
data class DeletionImpact(
    /** Items referenced only by that arrangement: they are deleted with it. */
    val exclusiveItems: List<PoolItem>,
    /** Items also referenced elsewhere: they stay. */
    val sharedItems: List<PoolItem>,
) {
    val exclusiveWidgets: Int get() = exclusiveItems.count { it is PoolWidget }
}

/**
 * Remove-from-arrangement versus delete-from-pool, workspace deletion and uninstall pruning. Every result carries the
 * removed items and the widget host ids to release (deferred: see [HostIdDeletionQueue]).
 */
object PoolRemoval {
    /** The normal remove icon: drops [itemId]'s placement here; the item stays if another arrangement holds it. */
    fun remove(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        itemId: PoolItemId,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId]
        return when {
            arrangement == null -> rejected(PoolRejection.UNKNOWN_WORKSPACE)
            itemId !in PoolReferences.referencedIn(arrangement) -> rejected(PoolRejection.NOT_PLACED_HERE)
            else -> PoolResult.Done(PoolGc.finish(pool, PoolPages.dropPlacement(pool, workspaceId, itemId)))
        }
    }

    /** "Remove everywhere": drops every placement of [itemId] in every arrangement, then it is collected. */
    fun removeEverywhere(
        pool: PlacedItemPool,
        itemId: PoolItemId,
    ): PoolResult =
        if (itemId !in pool.items) {
            rejected(PoolRejection.UNKNOWN_ITEM)
        } else {
            val dropped =
                pool.arrangements.keys.fold(
                    pool,
                ) { acc, workspaceId -> PoolPages.dropPlacement(acc, workspaceId, itemId) }
            PoolResult.Done(PoolGc.finish(pool, dropped))
        }

    /** Drops [workspaceId]'s arrangement; items only it held are collected, shared ones stay. */
    fun deleteWorkspace(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
    ): PoolResult =
        if (workspaceId !in pool.arrangements) {
            rejected(PoolRejection.UNKNOWN_WORKSPACE)
        } else {
            PoolResult.Done(PoolGc.finish(pool, pool.copy(arrangements = pool.arrangements - workspaceId)))
        }

    fun deletionImpact(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
    ): DeletionImpact {
        val here = pool.arrangements[workspaceId]?.let(PoolReferences::referencedIn).orEmpty()
        val index = PoolReferences.index(pool)
        val (exclusive, shared) =
            here.mapNotNull { pool.items[it] }.partition {
                    item ->
                index[item.id].orEmpty().all { it.workspaceId == workspaceId }
            }
        return DeletionImpact(exclusive, shared)
    }

    /**
     * Prunes a confirmed *package removed* event for [profile] (never "absent from a snapshot", never a profile
     * pause): drops every app of that package, every folder entry of it, and folders left empty. Idempotent. Dock pins
     * and widgets of the package are not handled here.
     */
    fun pruneUninstalled(
        pool: PlacedItemPool,
        packageName: AppPackageName,
        profile: AppProfile,
    ): PoolEdit {
        fun gone(identity: com.riffle.core.domain.launcher.apps.AppIdentity) =
            identity.packageName == packageName && identity.profile == profile
        val items =
            pool.items.values.mapNotNull { item ->
                when (item) {
                    is PoolApp -> item.takeUnless { gone(it.appIdentity) }
                    is PoolFolder ->
                        item.copy(
                            entries =
                                item.entries.filterNot {
                                    gone(it.appIdentity)
                                },
                        ).takeIf { it.entries.isNotEmpty() || item.entries.isEmpty() }
                    is PoolWidget -> item
                }
            }.associateBy { it.id }
        return PoolGc.finish(pool, dropDangling(pool.copy(items = items)))
    }

    /** Removes placements whose item is missing. */
    internal fun dropDangling(pool: PlacedItemPool): PlacedItemPool =
        pool.copy(
            arrangements =
                pool.arrangements.mapValues { (_, arrangement) ->
                    arrangement.copy(
                        pages =
                            arrangement.pages.map {
                                    page ->
                                page.copy(placements = page.placements.filter { it.item in pool.items })
                            },
                    )
                },
        )
}

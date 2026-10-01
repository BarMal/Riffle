package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/**
 * Writes engine-edited pages back into the pool (the inverse of [ArrangementAdapter]).
 *
 * Engines mint ids from an ordinal counted in the one arrangement they were shown (`HomeShortcutEngine`), so a
 * minted id can equal an id used by an item of another arrangement. Every id that was not in the input is therefore
 * treated as NEW and gets a fresh pool-unique [PoolItemId]; an engine id is never trusted as pool-global. Items that
 * vanished lose their reference and are collected. Edits to a folder or widget that was in the input change the
 * single pool item (visible wherever it is placed, by design).
 */
object ArrangementIngest {
    fun ingest(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        edited: List<LauncherPage>,
        ids: WorkspaceIdFactory,
    ): PoolResult {
        val arrangement = pool.arrangements[workspaceId]
        return if (arrangement == null) {
            rejected(PoolRejection.UNKNOWN_WORKSPACE)
        } else {
            val known = knownIds(arrangement, pool)
            val items = firstOccurrences(edited)
            val idMap = assignIds(items, known, pool, ids)
            val updatedItems = pool.items + items.mapNotNull { upsert(it, idMap, pool) }.associateBy { it.id }
            val seen = HashSet<String>()
            val pages = edited.map { page -> toPage(page, idMap, seen) }
            val raw =
                pool.copy(
                    items = updatedItems,
                    arrangements = pool.arrangements + (workspaceId to arrangement.copy(pages = pages)),
                )
            PoolResult.Done(PoolGc.finish(pool, PoolValidation.repair(raw).pool))
        }
    }

    private fun knownIds(
        arrangement: Arrangement,
        pool: PlacedItemPool,
    ): Set<String> = PoolReferences.referencedIn(arrangement).filter { it in pool.items }.map { it.value }.toSet()

    private fun firstOccurrences(edited: List<LauncherPage>): List<LauncherItem> =
        edited.flatMap { it.items }
            .filter { it.placement != null && it.id.value.isNotBlank() }
            .distinctBy { it.id.value }

    private fun assignIds(
        items: List<LauncherItem>,
        known: Set<String>,
        pool: PlacedItemPool,
        ids: WorkspaceIdFactory,
    ): Map<String, PoolItemId> {
        val taken = pool.items.keys.toMutableSet()
        return items.associate { item ->
            val text = item.id.value
            text to
                if (text in known) {
                    PoolItemId(text)
                } else {
                    PoolIds.fresh(taken, ids).also { taken += it }
                }
        }
    }

    private fun upsert(
        item: LauncherItem,
        idMap: Map<String, PoolItemId>,
        pool: PlacedItemPool,
    ): PoolItem? = idMap[item.id.value]?.let { id -> pool.items[id]?.let { update(it, item) } ?: create(id, item) }

    private fun update(
        existing: PoolItem,
        edited: LauncherItem,
    ): PoolItem =
        when {
            existing is PoolFolder && edited is FolderItem ->
                existing.copy(label = edited.label, entries = ArrangementAdapter.entriesOf(edited))
            existing is PoolWidget && edited is WidgetItem ->
                existing.copy(
                    resizeConstraints = edited.resizeConstraints,
                )
            existing is PoolApp && edited is AppShortcutItem -> existing.copy(label = edited.label)
            else -> existing
        }

    internal fun create(
        id: PoolItemId,
        item: LauncherItem,
    ): PoolItem =
        when (item) {
            is AppShortcutItem -> PoolApp(id, item.appIdentity, item.label, item.appShortcutId)
            is FolderItem -> PoolFolder(id, item.label, ArrangementAdapter.entriesOf(item))
            is WidgetItem ->
                PoolWidget(
                    id = id,
                    label = item.label,
                    resizeConstraints = item.resizeConstraints,
                    hostedId = ArrangementAdapter.hostedIdOrNull(item.appWidgetId),
                )
        }

    private fun toPage(
        page: LauncherPage,
        idMap: Map<String, PoolItemId>,
        seen: MutableSet<String>,
    ): ArrangementPage =
        ArrangementPage(
            id = page.id,
            grid = page.grid,
            placements =
                page.items.mapNotNull { item ->
                    val id = idMap[item.id.value]
                    val at = item.placement
                    if (id != null && at != null && seen.add(item.id.value)) Placement(id, at) else null
                },
            generatedContentOverflowCount = page.generatedContentOverflowCount,
            isPinned = page.isPinned,
        )
}

internal object PoolIds {
    /** A new id absent from [taken], drawn from [ids] (repeated draws skip any collision). */
    fun fresh(
        taken: Set<PoolItemId>,
        ids: WorkspaceIdFactory,
    ): PoolItemId {
        var candidate = PoolItemId(ids.next())
        while (candidate in taken) candidate = PoolItemId(ids.next())
        return candidate
    }
}

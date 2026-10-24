package com.riffle.core.domain.launcher.workspace.backup

import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutFold
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolItem
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget

/** What a restore produced, for a "3 workspaces, 5 saved lenses" style summary and for tests. */
data class WorkspaceRestore(
    val set: WorkspaceSet,
    val workspaceCount: Int,
    val savedLensCount: Int,
    /** Library refs that named no saved lens and were turned into the inline lens they already drew. */
    val repairedRefCount: Int,
)

/** What a pool restore produced: the state to store and how many widgets came back unbound. */
data class PoolRestore(
    val state: PoolStoreState,
    /** Widgets that came back as unbound placeholders (host ids are device-local). */
    val widgetPlaceholderCount: Int,
)

/**
 * Pure mapping between the stored workspace data and what a backup document carries.
 *
 * Only structure is ever written (workspaces, saved lenses, placed items, exclusion rules), never item
 * content. A hosted widget id is a live platform instance on this device and meaningless on another (or
 * after clearing data), so it is never written and every restored widget is an unbound placeholder that
 * keeps its cell and span. The section versions are the codecs' own schema versions; the document version
 * stays 1 so an older build still reads the file and ignores the keys it does not know.
 *
 * Placed items are their own section, mirroring the separate pool store: restoring workspaces never touches
 * them and the other way round. Arrangements are not pruned against the restored workspaces because migrated
 * arrangements are keyed by the migrated workspace ids, which preset workspaces borrow (see `PoolHomeView`).
 *
 * Nothing here throws: absent or empty data maps to null, which callers use to omit the section on export
 * and to leave current data untouched on import.
 */
object WorkspaceBackup {
    /** The set to write, or null when there is nothing to write (so the section is omitted). */
    fun exportSet(set: WorkspaceSet?): WorkspaceSet? = set?.takeIf { it.layouts.isNotEmpty() }

    /**
     * A usable restore of [set], or null when it is null or holds no layout (leave current data alone).
     * Refs that resolve take the library's lens ([LensLibraryOps.rehydrate]) and dangling lens refs are
     * detached to the inline lens they already drew. An older file's landscape layout is folded into the phone
     * one (N10, [WorkspaceLayoutFold]).
     */
    fun restoreSet(set: WorkspaceSet?): WorkspaceRestore? {
        if (set == null || set.layouts.isEmpty()) return null
        var repaired = 0
        val layouts =
            WorkspaceLayoutFold.fold(set).layouts.mapValues { (_, layout) ->
                val hydrated = LensLibraryOps.rehydrate(layout)
                repaired += hydrated.dangling.size
                LensLibraryOps.detachDangling(hydrated.layout)
            }
        return WorkspaceRestore(
            set = WorkspaceSet(layouts),
            workspaceCount = layouts.values.sumOf { it.workspaces.size },
            savedLensCount = layouts.values.sumOf { it.library.lenses.size },
            repairedRefCount = repaired,
        )
    }

    /**
     * The pool state to write without host ids, or null when nothing was ever stored (no pool and the
     * one-time import has not run), so the section is omitted.
     */
    fun exportPool(state: PoolStoreState?): PoolStoreState? =
        state?.takeIf { it.pools.isNotEmpty() || it.migrated }
            ?.let { it.copy(pools = it.pools.mapValues { (_, pool) -> pool.withoutHostIds() }) }

    /**
     * A usable restore of [state], or null to leave the current pool untouched. Host ids are dropped and the
     * import flag is kept as the backup recorded it, so a restored pool is not overwritten by a re-import.
     */
    fun restorePool(state: PoolStoreState?): PoolRestore? =
        exportPool(state)?.let { restored ->
            PoolRestore(
                state = restored,
                widgetPlaceholderCount = restored.pools.values.sumOf { pool -> pool.items.values.count(::isWidget) },
            )
        }

    /** The rules to write, or null when none were ever stored (an empty, unmigrated value is omitted). */
    fun exportExclusions(rules: LayoutExclusionRules?): LayoutExclusionRules? =
        rules?.takeIf { it.layouts.isNotEmpty() || it.legacyMigrated }

    /**
     * The rules to restore, or null to leave the current rules untouched. An older file's landscape rules are
     * merged into the phone ones (N10, [LayoutExclusionRules.folded]).
     */
    fun restoreExclusions(rules: LayoutExclusionRules?): LayoutExclusionRules? = exportExclusions(rules)?.folded()
}

private fun isWidget(item: PoolItem): Boolean = item is PoolWidget

private fun PlacedItemPool.withoutHostIds(): PlacedItemPool {
    if (items.values.none { it is PoolWidget && it.hostedId != null }) return this
    return copy(items = items.mapValues { (_, item) -> if (item is PoolWidget) item.copy(hostedId = null) else item })
}

package com.riffle.core.domain.launcher.workspace.backup

import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget

/** What a restore produced, for a "3 workspaces, 5 saved lenses" style summary and for tests. */
data class WorkspaceRestore(
    val set: WorkspaceSet,
    val workspaceCount: Int,
    val savedLensCount: Int,
    /** Widgets that came back as unbound placeholders (host ids are device-local). */
    val widgetPlaceholderCount: Int,
    /** Library refs that named no saved lens and were turned into the inline lens they already drew. */
    val repairedRefCount: Int,
)

/**
 * Pure mapping between the stored workspace data and what a backup document carries.
 *
 * Only structure is ever written (workspaces, saved lenses, pool entries, exclusion rules), never item
 * content. A hosted widget id is a live platform instance on this device and meaningless on another (or
 * after clearing data), so it is never written and every restored widget is an unbound placeholder that
 * keeps its cell and span. The section versions are the codecs' own schema versions; the document version
 * stays 1 so an older build still reads the file and ignores the keys it does not know.
 *
 * Nothing here throws: absent or empty data maps to null, which callers use to omit the section on export
 * and to leave current data untouched on import.
 */
object WorkspaceBackup {
    /** The set to write, or null when there is nothing to write (so the section is omitted). */
    fun exportSet(set: WorkspaceSet?): WorkspaceSet? =
        set?.takeIf { it.layouts.isNotEmpty() }
            ?.let { WorkspaceSet(it.layouts.mapValues { (_, layout) -> layout.withoutHostIds() }) }

    /**
     * A usable restore of [set], or null when it is null or holds no layout (leave current data alone).
     * Host ids are dropped, refs that resolve take the library's lens ([LensLibraryOps.rehydrate]) and
     * dangling lens refs are detached to the inline lens they already drew.
     */
    fun restoreSet(set: WorkspaceSet?): WorkspaceRestore? {
        if (set == null || set.layouts.isEmpty()) return null
        var placeholders = 0
        var repaired = 0
        val layouts =
            set.layouts.mapValues { (_, layout) ->
                placeholders += layout.pool.items.values.count { it is PoolWidget }
                val hydrated = LensLibraryOps.rehydrate(layout.withoutHostIds())
                repaired += hydrated.dangling.size
                LensLibraryOps.detachDangling(hydrated.layout)
            }
        return WorkspaceRestore(
            set = WorkspaceSet(layouts),
            workspaceCount = layouts.values.sumOf { it.workspaces.size },
            savedLensCount = layouts.values.sumOf { it.library.lenses.size },
            widgetPlaceholderCount = placeholders,
            repairedRefCount = repaired,
        )
    }

    /** The rules to write, or null when none were ever stored (an empty, unmigrated value is omitted). */
    fun exportExclusions(rules: LayoutExclusionRules?): LayoutExclusionRules? =
        rules?.takeIf { it.layouts.isNotEmpty() || it.legacyMigrated }

    /** The rules to restore, or null to leave the current rules untouched. */
    fun restoreExclusions(rules: LayoutExclusionRules?): LayoutExclusionRules? = exportExclusions(rules)
}

private fun LayoutWorkspaces.withoutHostIds(): LayoutWorkspaces {
    if (pool.items.values.none { it is PoolWidget && it.hostedId != null }) return this
    val items = pool.items.mapValues { (_, item) -> if (item is PoolWidget) item.copy(hostedId = null) else item }
    return copy(pool = pool.copy(items = items))
}

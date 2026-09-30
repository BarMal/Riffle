package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * One page of an expanded page-set. [key] is stable across evaluations: it derives from the group key
 * only, never from the group's position, so a pager can keep its selection and per-page state when
 * groups appear, disappear or reorder.
 */
data class PageSetPage(
    val key: String,
    val groupKey: String,
    val label: String?,
    val items: List<Item>,
)

/** What a page-set draws for one lens result. */
data class PageSetPlan(
    val pages: List<PageSetPage>,
    /** Groups beyond [PageSetPlanner.MAX_PAGES] that were not turned into pages. */
    val truncatedGroupCount: Int = 0,
    /** The result was not grouped, so no pages could be derived (the editor should prevent this). */
    val shapeMismatch: Boolean = false,
) {
    val isEmpty: Boolean get() = pages.isEmpty()

    fun indexOfKey(key: String?): Int = if (key == null) -1 else pages.indexOfFirst { it.key == key }

    /**
     * The page to show after the plan changed: the page with [previousKey] if it still exists, else the
     * page now at [previousIndex] (clamped), else 0. An empty plan always yields 0.
     */
    fun reconcileSelection(
        previousKey: String?,
        previousIndex: Int,
    ): Int {
        val byKey = indexOfKey(previousKey)
        return when {
            pages.isEmpty() -> 0
            byKey >= 0 -> byKey
            else -> previousIndex.coerceIn(0, pages.lastIndex)
        }
    }
}

/** Expands a grouped [LensResult] into pages. Pure; holds no state. */
object PageSetPlanner {
    const val MAX_PAGES = 32
    private const val KEY_PREFIX = "group:"

    fun pageKeyFor(groupKey: String): String = KEY_PREFIX + groupKey

    /**
     * One page per non-empty group, in group order. Empty groups are skipped (a page with nothing to draw
     * is noise, and the dock's jump targets must not list them). Duplicate group keys keep their first
     * group so page keys stay unique. At most [maxPages] pages are produced; the rest are counted.
     */
    fun plan(
        result: LensResult,
        maxPages: Int = MAX_PAGES,
    ): PageSetPlan {
        require(maxPages > 0) { "A page-set must allow at least one page." }
        val grouped = result as? LensResult.Grouped ?: return PageSetPlan(emptyList(), shapeMismatch = true)
        val seen = HashSet<String>()
        val candidates = grouped.groups.filter { it.items.isNotEmpty() && seen.add(it.key) }
        val pages =
            candidates.take(maxPages).map { group ->
                PageSetPage(pageKeyFor(group.key), group.key, group.label, group.items)
            }
        return PageSetPlan(pages, truncatedGroupCount = candidates.size - pages.size)
    }
}

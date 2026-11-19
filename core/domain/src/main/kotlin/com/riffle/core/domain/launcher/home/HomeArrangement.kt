package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryCategories
import java.util.Locale

/** What "Add all apps to Home" did: the new layout and how many apps it placed. */
data class HomeAddAllResult(
    val layout: HomeLayout,
    val added: Int,
)

/**
 * The two bulk rules of Settings > Home (ADR 0003, Amendment 8; `settings.md`). Pure and never throws.
 *
 * - [missingApps] / [addAll]: every visible, launchable app that is not on Home (on a page, in a folder or in the
 *   Dock), placed after the last occupied cell in the Library's A to Z order, adding pages as needed. Nothing that is
 *   already placed moves.
 * - [arrange]: Home's apps and folders re-laid out A to Z, page after page, flowing around widgets (which keep their
 *   page and cells). The Dock is not touched.
 */
@Suppress("TooManyFunctions") // Small private helpers around the three entry points.
object HomeArrangement {
    /** With "Compact" off a page is filled to this share of its cells before the next page starts. */
    const val ROOMY_FILL_PERCENT = 75

    /** How many cells of a page of [cells] cells are filled before the next page starts. */
    fun fillLimit(
        cells: Int,
        compact: Boolean,
    ): Int = if (compact) cells.coerceAtLeast(1) else (cells * ROOMY_FILL_PERCENT / PERCENT).coerceAtLeast(1)

    /** The apps of [apps] that Home does not show yet (pages, folders, Dock), in the Library's order, once each. */
    fun missingApps(
        layout: HomeLayout,
        apps: List<InstalledApp>,
    ): List<InstalledApp> =
        apps
            .distinctBy { it.identity }
            .filter { it.visibility == AppVisibility.VISIBLE && !layout.containsHomeAppOrDock(it.identity) }
            .sortedWith(LibraryCategories.appOrder)

    /** [layout] with every [missingApps] app appended after the last occupied cell (0 added when none is missing). */
    fun addAll(
        layout: HomeLayout,
        apps: List<InstalledApp>,
    ): HomeAddAllResult {
        var current = layout
        var added = 0
        missingApps(layout, apps).forEach { app ->
            HomeAppPlacement.appendApp(current, app)?.let {
                current = it
                added++
            }
        }
        return HomeAddAllResult(current, added)
    }

    /**
     * [layout]'s Home pages re-laid out A to Z. Apps and folders sort together by label (the Library's order; folders
     * by name, ties keep their reading order, so running it twice changes nothing). With [compact] each page is
     * filled completely before the next starts; otherwise to [ROOMY_FILL_PERCENT] of its cells (widget cells count).
     * Widgets stay on their page and cells and icons flow around them; a page with a widget is never dropped. [newApps]
     * join the sorted items (used to seed a Home); trailing empty, unpinned Home pages are removed, but one stays.
     * Pages of other types and the Dock are left as they are.
     */
    fun arrange(
        layout: HomeLayout,
        compact: Boolean,
        newApps: List<InstalledApp> = emptyList(),
    ): HomeLayout {
        val homePages = layout.pages.filter { it.type == LauncherPageType.Home }
        if (homePages.isEmpty()) return layout
        val placed =
            homePages
                .flatMap { page -> page.items.filter { it !is WidgetItem }.sortedBy(::readingIndex) }
        val fresh = newApps.distinctBy { it.identity }.map { HomeAppPlacement.itemFor(layout, it) }
        val sorted = (placed + fresh).sortedWith(compareBy { labelKey(it) })
        val template = homePages.last().grid
        val usedIds = layout.pages.map { it.id.value }.toMutableSet()
        val flowed = flow(homePages, sorted, compact, template, usedIds)
        val trimmed = flowed.trimTrailing()
        val pages = layout.pages.mergedWith(homePages, trimmed)
        val selected =
            if (pages.any { it.id == layout.selectedPageId }) layout.selectedPageId else trimmed.last().id
        return layout.copy(pages = pages, selectedPageId = selected)
    }

    private fun flow(
        homePages: List<LauncherPage>,
        sorted: List<LauncherItem>,
        compact: Boolean,
        template: GridDimensions,
        usedIds: MutableSet<String>,
    ): List<LauncherPage> {
        val out = mutableListOf<LauncherPage>()
        var next = 0
        var index = 0
        while (next < sorted.size || index < homePages.size) {
            val existing = homePages.getOrNull(index)
            val page = existing ?: LauncherPage(id = freshId(usedIds), grid = template)
            usedIds += page.id.value
            val widgets = page.items.filterIsInstance<WidgetItem>()
            val covered = widgets.flatMap { coveredCells(it, page.grid) }.toSet()
            val free = (0 until page.grid.cellCount).filter { it !in covered }
            val allowed = (fillLimit(page.grid.cellCount, compact) - covered.size).coerceIn(0, free.size)
            val take = minOf(allowed, sorted.size - next)
            if (existing == null && take == 0) break
            val icons =
                (0 until take).map { slot ->
                    val cell = free[slot]
                    sorted[next + slot].withPlacement(
                        GridPlacement(GridCell(column = cell % page.grid.columns, row = cell / page.grid.columns)),
                    )
                }
            next += take
            out += page.copy(items = widgets + icons)
            index++
        }
        return out
    }

    private fun List<LauncherPage>.trimTrailing(): List<LauncherPage> {
        var end = size
        while (end > 1 && this[end - 1].items.isEmpty() && !this[end - 1].isPinned) end--
        return take(end)
    }

    /** The original pages with the Home pages replaced by [replacement], extra pages following the last Home page. */
    private fun List<LauncherPage>.mergedWith(
        homePages: List<LauncherPage>,
        replacement: List<LauncherPage>,
    ): List<LauncherPage> {
        val result = mutableListOf<LauncherPage>()
        var taken = 0
        var homeSeen = 0
        forEach { page ->
            if (page.type != LauncherPageType.Home) {
                result += page
            } else if (++homeSeen < homePages.size) {
                replacement.getOrNull(taken)?.let {
                    result += it
                    taken++
                }
            } else {
                while (taken < replacement.size) result += replacement[taken++]
            }
        }
        return result
    }

    private fun coveredCells(
        widget: WidgetItem,
        grid: GridDimensions,
    ): List<Int> {
        val placement = widget.placement ?: return emptyList()
        return (placement.cell.row until placement.cell.row + placement.span.rows).flatMap { row ->
            (placement.cell.column until placement.cell.column + placement.span.columns).mapNotNull { column ->
                if (row in 0 until grid.rows && column in 0 until grid.columns) row * grid.columns + column else null
            }
        }
    }

    private fun readingIndex(item: LauncherItem): Int =
        item.placement?.let { it.cell.row * READING_ROW_WEIGHT + it.cell.column } ?: Int.MAX_VALUE

    private fun labelKey(item: LauncherItem): String =
        when (item) {
            is AppShortcutItem -> item.label
            is FolderItem -> item.label
            is WidgetItem -> item.label
        }.lowercase(Locale.ROOT)

    private fun freshId(used: Set<String>): LauncherPageId =
        generateSequence(2) { it + 1 }.map { "home:$it" }.first { it !in used }.let { LauncherPageId(it) }

    private const val PERCENT = 100
    private const val READING_ROW_WEIGHT = 1_000
}

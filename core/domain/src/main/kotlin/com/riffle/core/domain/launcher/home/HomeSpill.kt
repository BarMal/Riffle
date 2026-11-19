package com.riffle.core.domain.launcher.home

import kotlin.math.max

/**
 * Where a dropped item goes when its page cannot make room (the Home drag, "Spill"). Pure and never throws; nothing is
 * ever lost: every item that was dropped ends on a page, in the layout the result holds.
 *
 * - [drop]: an item dropped at a cell of a page, after the neighbours the push moved. When the landing is still taken
 *   by items the push could not move, the displaced items (apps and folders only) go to the first free cell of the next
 *   page and the dropped item takes the cell; when a widget or a stack is in the way nothing is displaced, because a
 *   widget or a stack is never moved unless it is the dragged item: the dropped item itself goes to the next page.
 * - [dropGroup]: several items placed from the drop cell onward in reading order, the rest spilling the same way.
 *
 * "The next page" is the first Home page after the target with a free spot for the item's span; with none, a new page
 * is added after the last Home page. Generated pages never take an item.
 */
@Suppress("TooManyFunctions", "ReturnCount") // Small private helpers; early returns for the rejections.
object HomeSpill {
    private val engine = GridPlacementEngine()

    /**
     * [item] (not in [layout]; its placement gives its span) dropped at [cell] of [targetPageId] after [pushed] moved.
     * Rejected only when the target is missing or generated, or the item does not fit a page at all.
     */
    @Suppress("ReturnCount")
    fun drop(
        layout: HomeLayout,
        item: LauncherItem,
        targetPageId: LauncherPageId,
        cell: GridCell,
        pushed: Map<LauncherItemId, GridCell> = emptyMap(),
    ): HomeShortcutResult {
        val target = layout.pages.firstOrNull { it.id == targetPageId }
        if (target == null || target.type is LauncherPageType.Generated) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        val span = item.placement?.span ?: GridSpan()
        if (span.columns > target.grid.columns || span.rows > target.grid.rows) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.OUT_OF_BOUNDS)
        }
        val landing =
            GridCell(
                column = cell.column.coerceIn(0, target.grid.columns - span.columns),
                row = cell.row.coerceIn(0, target.grid.rows - span.rows),
            )
        val dropped = item.withPlacement(GridPlacement(landing, span))
        val moved = withPushed(target, pushed)
        val blockers = moved.items.filter { it.overlaps(dropped) }
        val onPage =
            when {
                blockers.isEmpty() -> engine.placeItem(moved, dropped) as? PlaceLauncherItemResult.Placed
                blockers.all { it is AppShortcutItem || it is FolderItem } ->
                    engine.placeItem(blockers.fold(moved) { page, b -> engine.removeItem(page, b.id) }, dropped)
                        as? PlaceLauncherItemResult.Placed
                else -> null
            }
        if (onPage != null) {
            val withPage = layout.replacing(onPage.page)
            val displaced = blockers.sortedBy { it.readingIndex(target.grid) }
            return spillAll(withPage, targetPageId, displaced.map { it.withoutPlacement() })
        }
        return spillAll(layout, targetPageId, listOf(dropped.withoutPlacement()))
    }

    /**
     * The item [itemId] of [sourcePageId] moved to [cell] of [targetPageId] with the spill rule ([drop] after taking
     * it off its page). Rejected, with the layout unchanged, when the item or a page is missing or the target is
     * generated.
     */
    fun dropMoved(
        layout: HomeLayout,
        itemId: LauncherItemId,
        sourcePageId: LauncherPageId,
        targetPageId: LauncherPageId,
        cell: GridCell,
        pushed: Map<LauncherItemId, GridCell> = emptyMap(),
    ): HomeShortcutResult {
        val source = layout.pages.firstOrNull { it.id == sourcePageId }
        val item = source?.items?.firstOrNull { it.id == itemId }
        if (source == null || item == null || source.type is LauncherPageType.Generated) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        val without = layout.replacing(engine.removeItem(source, itemId))
        return drop(without, item, targetPageId, cell, pushed)
    }

    /**
     * [items] (not in [layout]) placed from [cell] of [targetPageId] onward in reading order: each takes the next free
     * spot at or after the previous one, the rest spilling to the following pages. Rejected only for a missing or
     * generated target.
     */
    fun dropGroup(
        layout: HomeLayout,
        items: List<LauncherItem>,
        targetPageId: LauncherPageId,
        cell: GridCell,
    ): HomeShortcutResult {
        val target = layout.pages.firstOrNull { it.id == targetPageId }
        if (target == null || target.type is LauncherPageType.Generated) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        var current = layout
        var after = cell.row * target.grid.columns + cell.column - 1
        val leftover = mutableListOf<LauncherItem>()
        items.forEach { item ->
            val page = current.pages.first { it.id == targetPageId }
            val spot = page.firstFreeAfter(item, after)
            if (spot == null) {
                leftover += item
            } else {
                current = current.replacing(spot.first)
                after = spot.second
            }
        }
        return spillAll(current, targetPageId, leftover.map { it.withoutPlacement() })
    }

    private fun spillAll(
        layout: HomeLayout,
        fromPageId: LauncherPageId,
        items: List<LauncherItem>,
    ): HomeShortcutResult {
        var current = layout
        for (item in items) {
            current = spill(current, fromPageId, item) ?: return HomeShortcutResult.Rejected(
                PlacementRejectionReason.NO_AVAILABLE_CELL,
            )
        }
        return HomeShortcutResult.Updated(current)
    }

    /** [item] on the first Home page after [fromPageId] with room for it, or on a new page after the last Home page. */
    private fun spill(
        layout: HomeLayout,
        fromPageId: LauncherPageId,
        item: LauncherItem,
    ): HomeLayout? {
        val pages = layout.pages
        val from = pages.indexOfFirst { it.id == fromPageId }
        for (index in pages.indices.filter { it > from && pages[it].type == LauncherPageType.Home }) {
            val spot = pages[index].firstFreeAfter(item, -1) ?: continue
            return layout.replacing(spot.first)
        }
        val lastHome = pages.indexOfLast { it.type == LauncherPageType.Home }
        val template = pages.getOrNull(lastHome)?.grid ?: pages.getOrNull(from)?.grid ?: return null
        val fresh = LauncherPage(id = freshPageId(layout), grid = template)
        val spot = fresh.firstFreeAfter(item, -1) ?: return null
        val at = max(lastHome, from) + 1
        return layout.copy(pages = pages.take(at) + spot.first + pages.drop(at))
    }

    private fun withPushed(
        page: LauncherPage,
        pushed: Map<LauncherItemId, GridCell>,
    ): LauncherPage {
        if (pushed.isEmpty()) return page
        val movers = page.items.filter { it.id in pushed && it.placement != null }
        val cleared = movers.fold(page) { acc, mover -> engine.removeItem(acc, mover.id) }
        var working = cleared
        for (mover in movers) {
            val at = mover.withPlacement(mover.placement!!.copy(cell = pushed.getValue(mover.id)))
            working = (engine.placeItem(working, at) as? PlaceLauncherItemResult.Placed)?.page ?: return page
        }
        return working
    }

    /** The page with [item] in the first free spot after reading index [after], and that spot's index. */
    private fun LauncherPage.firstFreeAfter(
        item: LauncherItem,
        after: Int,
    ): Pair<LauncherPage, Int>? {
        val span = item.placement?.span ?: GridSpan()
        for (index in after + 1 until grid.cellCount) {
            val cell = GridCell(index % grid.columns, index / grid.columns)
            val candidate = item.withPlacement(GridPlacement(cell, span))
            val placed = engine.placeItem(this, candidate) as? PlaceLauncherItemResult.Placed
            if (placed != null) return placed.page to index
        }
        return null
    }

    private fun HomeLayout.replacing(page: LauncherPage): HomeLayout =
        copy(pages = pages.map { if (it.id == page.id) page else it })

    private fun LauncherItem.overlaps(other: LauncherItem): Boolean {
        val a = placement ?: return false
        val b = other.placement ?: return false
        return a.cell.column < b.cell.column + b.span.columns && b.cell.column < a.cell.column + a.span.columns &&
            a.cell.row < b.cell.row + b.span.rows && b.cell.row < a.cell.row + a.span.rows
    }

    private fun LauncherItem.readingIndex(grid: GridDimensions): Int =
        placement?.let { it.cell.row * grid.columns + it.cell.column } ?: Int.MAX_VALUE

    /** The item keeps its span; where it lands is chosen by the spill. */
    private fun LauncherItem.withoutPlacement(): LauncherItem =
        withPlacement(GridPlacement(GridCell(0, 0), placement?.span ?: GridSpan()))

    private fun freshPageId(layout: HomeLayout): LauncherPageId {
        val used = layout.pages.map { it.id.value }.toSet()
        return generateSequence(2) { it + 1 }.map { "home:$it" }.first { it !in used }.let { LauncherPageId(it) }
    }
}

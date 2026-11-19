package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

/**
 * Where an app goes on the one Home (ADR 0003, Amendment 8): a user-arranged grid of placed items on pages. Pure and
 * never throws.
 *
 * - [appendApp] puts an app in the first free cell after the last occupied one (a new page when there is none): what
 *   "Add to Home" and "Add new apps to Home" do.
 * - [filledWith] builds the Home a user whose Home was the app library gets once: every app in the given order, page
 *   by page, pages filled fully, with the placed items it had NOT merged in.
 */
@Suppress("TooManyFunctions") // Small private helpers around two entry points.
object HomeAppPlacement {
    private val engine = GridPlacementEngine()

    /** The item an app is placed as; [layout] gives the next free ordinal for the id. */
    fun itemFor(
        layout: HomeLayout,
        app: InstalledApp,
    ): AppShortcutItem = itemFor(app.identity, app.label, ordinal = layout.countOf(app.identity) + 1)

    /**
     * [layout] with [app] in the first free 1x1 cell after the last occupied cell of the Home pages (reading order:
     * rows top to bottom, columns left to right, pages in order), moving on to the next page when the rest of a page
     * is taken and adding a page when every page is. Returns null when Home already shows the app (in a folder
     * too) or has no page that can take it.
     */
    @Suppress("ReturnCount")
    fun appendApp(
        layout: HomeLayout,
        app: InstalledApp,
    ): HomeLayout? {
        if (layout.pages.none { it.type == LauncherPageType.Home } || layout.containsHomeApp(app.identity)) return null
        val item = itemFor(layout, app)
        val pages = layout.pages
        val homeIndices = pages.indices.filter { pages[it].type == LauncherPageType.Home }
        val lastUsed = homeIndices.lastOrNull { pages[it].items.isNotEmpty() }
        val start = lastUsed ?: homeIndices.first()
        for (index in homeIndices.filter { it >= start }) {
            val page = pages[index]
            val after = if (index == start && lastUsed != null) page.lastOccupiedIndex() else -1
            val placed = page.placeAfter(item, after)
            if (placed != null) return layout.copy(pages = pages.replaced(index, placed))
        }
        val grid = pages[homeIndices.last()].grid
        val fresh = LauncherPage(id = freshPageId(layout), grid = grid)
        val placed = fresh.placeAfter(item, -1) ?: return null
        return layout.copy(pages = pages.insertAfter(homeIndices.last(), placed))
    }

    /**
     * [layout] with its Home pages holding exactly [apps], in order, each page filled before the next one starts. The
     * existing Home pages are reused in order (their ids and grids kept), more pages are added as needed and
     * unneeded trailing Home pages go; pages of other types stay. Items the Home had are not carried over.
     */
    fun filledWith(
        layout: HomeLayout,
        apps: List<InstalledApp>,
    ): HomeLayout {
        val homePages = layout.pages.filter { it.type == LauncherPageType.Home }
        val template = homePages.firstOrNull() ?: LauncherPage(id = LauncherPageId("home"), grid = layout.workspaceGrid)
        val capacity = template.grid.cellCount.coerceAtLeast(1)
        val chunks = apps.distinctBy { it.identity }.chunked(capacity).ifEmpty { listOf(emptyList()) }
        val usedIds = layout.pages.map { it.id.value }.toMutableSet()
        val filled =
            chunks.mapIndexed { index, chunk ->
                val base =
                    homePages.getOrNull(index)
                        ?: LauncherPage(id = nextId(usedIds), grid = template.grid)
                usedIds += base.id.value
                base.copy(items = chunk.mapIndexed { cell, app -> app.placedAt(template.grid, cell) })
            }
        val kept = layout.pages.filter { it.type != LauncherPageType.Home && it.type != LauncherPageType.AllApps }
        val firstHome = layout.pages.indexOfFirst { it.type == LauncherPageType.Home }.coerceAtLeast(0)
        val before = kept.take(firstHome)
        val after = kept.drop(firstHome)
        val pages = before + filled + after
        val selected = pages.firstOrNull { it.id == layout.selectedPageId }?.id ?: filled.first().id
        return layout.copy(pages = pages, selectedPageId = selected)
    }

    private fun InstalledApp.placedAt(
        grid: GridDimensions,
        cell: Int,
    ): AppShortcutItem =
        itemFor(identity, label, ordinal = 1)
            .copy(placement = GridPlacement(GridCell(column = cell % grid.columns, row = cell / grid.columns)))

    private fun itemFor(
        identity: AppIdentity,
        label: String,
        ordinal: Int,
    ): AppShortcutItem =
        AppShortcutItem(
            id =
                LauncherItemId(
                    "app:${identity.profile.id.value}:${identity.packageName.value}/" +
                        "${identity.activityName.value}:$ordinal",
                ),
            appIdentity = identity,
            label = label,
        )

    private fun HomeLayout.countOf(identity: AppIdentity): Int =
        pages.flatMap { it.items }.filterIsInstance<AppShortcutItem>()
            .count { it.appIdentity == identity && it.appShortcutId == null }

    /** The reading-order index (row * columns + column) of the last occupied cell of this page, or -1. */
    private fun LauncherPage.lastOccupiedIndex(): Int =
        items.maxOfOrNull { item ->
            val placement = item.placement ?: return@maxOfOrNull -1
            val lastRow = placement.cell.row + placement.span.rows - 1
            val lastColumn = placement.cell.column + placement.span.columns - 1
            // The last cell in reading order among the span's cells is its bottom-right one.
            lastRow * grid.columns + lastColumn
        } ?: -1

    private fun LauncherPage.placeAfter(
        item: AppShortcutItem,
        after: Int,
    ): LauncherPage? =
        (after + 1 until grid.cellCount).firstNotNullOfOrNull { index ->
            val candidate = item.withPlacement(GridPlacement(GridCell(index % grid.columns, index / grid.columns)))
            (engine.placeItem(this, candidate) as? PlaceLauncherItemResult.Placed)?.page
        }

    private fun List<LauncherPage>.replaced(
        index: Int,
        page: LauncherPage,
    ): List<LauncherPage> = mapIndexed { i, existing -> if (i == index) page else existing }

    private fun List<LauncherPage>.insertAfter(
        index: Int,
        page: LauncherPage,
    ): List<LauncherPage> = take(index + 1) + page + drop(index + 1)

    private fun freshPageId(layout: HomeLayout): LauncherPageId =
        nextId(layout.pages.map { it.id.value }.toMutableSet())

    private fun nextId(used: MutableSet<String>): LauncherPageId =
        generateSequence(2) { it + 1 }.map { "home:$it" }.first { it !in used }.let { LauncherPageId(it) }
}

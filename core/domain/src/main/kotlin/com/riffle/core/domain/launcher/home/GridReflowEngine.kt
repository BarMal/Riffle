package com.riffle.core.domain.launcher.home

/**
 * Re-fits a layout's pages to a different grid, moving whatever no longer fits.
 *
 * Growing a grid never displaces anything, so this exists for the shrinking case: a side dock takes
 * a column from the workspace, and the items that were in it have to go somewhere. Unlike
 * [HomePageEngine.updateGridDimensions] -- which refuses a shrink that would strand an item,
 * because there the user asked for that exact grid -- here the grid is a consequence of moving the
 * dock, and refusing would make a position setting silently do nothing.
 *
 * Displaced items are re-placed into the first free cell, preferring the page they were already on
 * so a reflow keeps things near where the user left them, and falling back to later pages and then
 * earlier ones. Only a layout with no free cell anywhere is rejected.
 */
class GridReflowEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun reflowToGrid(
        layout: HomeLayout,
        dimensions: GridDimensions,
    ): GridReflowResult {
        if (dimensions.columns < MIN_GRID_DIMENSION || dimensions.rows < MIN_GRID_DIMENSION) {
            return GridReflowResult.Rejected(PlacementRejectionReason.OUT_OF_BOUNDS)
        }

        val resized = layout.pages.map { page -> page.retaining(dimensions) }
        val displaced =
            layout.pages.flatMapIndexed { pageIndex, page ->
                page.items
                    .filterNot { item -> dimensions.holds(item.placement) }
                    .map { item -> DisplacedItem(item = item, originPageIndex = pageIndex) }
            }

        return displaced
            .fold(ReflowState(pages = resized.map { page -> page.copy(grid = dimensions) })) { state, entry ->
                state.placing(entry, gridPlacementEngine)
            }
            .let { state ->
                when {
                    state.strandedItem != null -> GridReflowResult.Rejected(PlacementRejectionReason.NO_AVAILABLE_CELL)
                    else -> GridReflowResult.Updated(layout.copy(pages = state.pages))
                }
            }
    }

    /** The page as it stands once everything the new grid cannot hold has been lifted off it. */
    private fun LauncherPage.retaining(dimensions: GridDimensions): LauncherPage =
        copy(items = items.filter { item -> dimensions.holds(item.placement) })
}

sealed interface GridReflowResult {
    data class Updated(val layout: HomeLayout) : GridReflowResult

    data class Rejected(val reason: PlacementRejectionReason) : GridReflowResult
}

private data class DisplacedItem(
    val item: LauncherItem,
    val originPageIndex: Int,
)

private data class ReflowState(
    val pages: List<LauncherPage>,
    val strandedItem: LauncherItem? = null,
) {
    fun placing(
        entry: DisplacedItem,
        gridPlacementEngine: GridPlacementEngine,
    ): ReflowState {
        if (strandedItem != null) return this
        return pages.indices
            .sortedBy { index -> if (index >= entry.originPageIndex) index - entry.originPageIndex else pages.size }
            .firstNotNullOfOrNull { index -> placedOnPage(index, entry.item, gridPlacementEngine) }
            ?: copy(strandedItem = entry.item)
    }

    private fun placedOnPage(
        index: Int,
        item: LauncherItem,
        gridPlacementEngine: GridPlacementEngine,
    ): ReflowState? {
        val page = pages[index]
        if (page.type is LauncherPageType.Generated) return null
        return item
            .spanCandidatesFor(page.grid)
            .firstNotNullOfOrNull { span ->
                (
                    gridPlacementEngine.placeItemInFirstAvailableCell(
                        page = page,
                        item = item,
                        span = span,
                    ) as? PlaceLauncherItemResult.Placed
                )?.page
            }
            ?.let { placed -> copy(pages = pages.toMutableList().apply { set(index, placed) }) }
    }
}

/**
 * The spans worth trying for a displaced item, largest first.
 *
 * Only a widget has a span to lose; everything else is a single cell. A widget narrower than the
 * grid keeps its shape, and one that no longer fits comes down through the sizes its own resize
 * constraints allow rather than being stranded at its old size.
 */
private fun LauncherItem.spanCandidatesFor(grid: GridDimensions): List<GridSpan> =
    when (this) {
        is WidgetItem ->
            (placement?.span ?: GridSpan())
                .placementCandidates(resizeConstraints)
                .filter { span -> span.columns <= grid.columns && span.rows <= grid.rows }

        is AppShortcutItem, is FolderItem -> listOf(GridSpan())
    }

/**
 * Re-fits a layout to the grid its dock leaves the workspace.
 *
 * Stored placements outlive the setting that shaped them -- a layout written with a bottom dock and
 * read back with a side one has items in a column that is no longer there. Without this they would
 * be held by the layout but drawn nowhere, which reads to the user as apps that vanished. A layout
 * with nowhere to reflow into is returned as it stands rather than refused, since a decode has no
 * one to refuse to.
 */
fun HomeLayout.reflowedToWorkspaceGrid(): HomeLayout =
    when (val result = GridReflowEngine().reflowToGrid(this, workspaceGrid)) {
        is GridReflowResult.Updated -> result.layout
        is GridReflowResult.Rejected -> this
    }

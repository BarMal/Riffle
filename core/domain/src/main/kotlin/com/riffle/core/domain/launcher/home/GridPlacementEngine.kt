package com.riffle.core.domain.launcher.home

class GridPlacementEngine {
    fun placeItem(
        page: LauncherPage,
        item: LauncherItem,
    ): PlaceLauncherItemResult =
        item.placement?.let { placement ->
            when {
                !page.grid.contains(placement) ->
                    PlaceLauncherItemResult.Rejected(PlacementRejectionReason.OUT_OF_BOUNDS)

                page.items.any { existingItem -> existingItem.collidesWith(item) } ->
                    PlaceLauncherItemResult.Rejected(PlacementRejectionReason.COLLISION)

                else ->
                    PlaceLauncherItemResult.Placed(page.copy(items = page.items + item))
            }
        } ?: PlaceLauncherItemResult.Rejected(PlacementRejectionReason.MISSING_PLACEMENT)

    private fun GridDimensions.contains(placement: GridPlacement): Boolean =
        placement.cell.column >= 0 &&
            placement.cell.row >= 0 &&
            placement.cell.column + placement.span.columns <= columns &&
            placement.cell.row + placement.span.rows <= rows

    private fun LauncherItem.collidesWith(other: LauncherItem): Boolean =
        placement?.occupiedCells.orEmpty().intersect(other.placement?.occupiedCells.orEmpty()).isNotEmpty()

    private val GridPlacement.occupiedCells: Set<GridCell>
        get() =
            (cell.column until cell.column + span.columns)
                .flatMap { column ->
                    (cell.row until cell.row + span.rows).map { row ->
                        GridCell(column = column, row = row)
                    }
                }
                .toSet()
}

sealed interface PlaceLauncherItemResult {
    data class Placed(val page: LauncherPage) : PlaceLauncherItemResult

    data class Rejected(val reason: PlacementRejectionReason) : PlaceLauncherItemResult
}

enum class PlacementRejectionReason {
    MISSING_PLACEMENT,
    OUT_OF_BOUNDS,
    COLLISION,
}

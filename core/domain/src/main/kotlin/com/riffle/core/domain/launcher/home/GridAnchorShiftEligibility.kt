package com.riffle.core.domain.launcher.home

internal fun LauncherPage.canShiftAnchorsFor(
    itemId: LauncherItemId,
    cell: GridCell,
): Boolean =
    items
        .firstOrNull { existingItem -> existingItem.id == itemId }
        ?.shiftedTo(cell)
        ?.let(::canShiftAnchorsFor)
        ?: false

private fun LauncherPage.canShiftAnchorsFor(shiftedItem: LauncherItem): Boolean =
    shiftedItem.placement?.span == GridSpan() &&
        items
            .filterNot { existingItem -> existingItem.id == shiftedItem.id }
            .filter { existingItem -> existingItem.collidesWith(shiftedItem) }
            .all { existingItem -> existingItem.placement?.span == GridSpan() }

private fun LauncherItem.shiftedTo(cell: GridCell): LauncherItem? =
    placement
        ?.copy(cell = cell)
        ?.let(::withPlacement)

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

package com.riffle.core.domain.launcher.home

internal class GridAnchorShiftPlanner {
    fun shiftedPage(
        page: LauncherPage,
        itemId: LauncherItemId,
        cell: GridCell,
    ): LauncherPage? =
        page.shiftRequest(itemId = itemId, cell = cell)
            ?.let { request ->
                val shiftedAnchors =
                    page.items
                        .itemsByAnchorIndex(page.grid)
                        .shiftedAnchors(originIndex = request.originIndex, targetIndex = request.targetIndex)

                page.copy(
                    items =
                        page.items.map { existingItem ->
                            existingItem.withShiftedPlacement(
                                itemId = itemId,
                                targetCell = cell,
                                shiftedAnchors = shiftedAnchors,
                                grid = page.grid,
                            )
                        },
                )
            }

    private fun LauncherPage.shiftRequest(
        itemId: LauncherItemId,
        cell: GridCell,
    ): AnchorShiftRequest? {
        val originIndex =
            items
                .firstOrNull { existingItem -> existingItem.id == itemId }
                ?.placement
                ?.cell
                ?.let { originCell -> grid.indexOf(originCell) }
        val targetIndex = grid.indexOf(cell)

        return when {
            originIndex != null && targetIndex != null ->
                AnchorShiftRequest(originIndex = originIndex, targetIndex = targetIndex)

            else -> null
        }
    }

    private fun List<LauncherItem>.itemsByAnchorIndex(grid: GridDimensions): MutableMap<Int, LauncherItem> =
        mapNotNull { item ->
            item.placement?.cell
                ?.let { cell -> grid.indexOf(cell) }
                ?.let { index -> index to item }
        }
            .toMap()
            .toMutableMap()

    private fun MutableMap<Int, LauncherItem>.shiftedAnchors(
        originIndex: Int,
        targetIndex: Int,
    ): Map<Int, LauncherItem> {
        remove(originIndex)
        shiftIndexes(originIndex = originIndex, targetIndex = targetIndex)
        return this
    }

    private fun MutableMap<Int, LauncherItem>.shiftIndexes(
        originIndex: Int,
        targetIndex: Int,
    ) {
        when {
            targetIndex > originIndex ->
                for (index in originIndex until targetIndex) {
                    moveIndex(from = index + 1, to = index)
                }

            else ->
                for (index in originIndex downTo targetIndex + 1) {
                    moveIndex(from = index - 1, to = index)
                }
        }
    }

    private fun LauncherItem.withShiftedPlacement(
        itemId: LauncherItemId,
        targetCell: GridCell,
        shiftedAnchors: Map<Int, LauncherItem>,
        grid: GridDimensions,
    ): LauncherItem =
        when (id) {
            itemId -> withPlacement(placement!!.copy(cell = targetCell))
            else ->
                shiftedCell(shiftedAnchors = shiftedAnchors, grid = grid)
                    ?.let { cell -> withPlacement(placement!!.copy(cell = cell)) }
                    ?: this
        }

    private fun LauncherItem.shiftedCell(
        shiftedAnchors: Map<Int, LauncherItem>,
        grid: GridDimensions,
    ): GridCell? =
        shiftedAnchors.entries
            .firstOrNull { (_, shiftedItem) -> shiftedItem.id == id }
            ?.key
            ?.let { index -> grid.cellAt(index) }

    private fun MutableMap<Int, LauncherItem>.moveIndex(
        from: Int,
        to: Int,
    ) {
        val item = remove(from)

        if (item == null) {
            remove(to)
        } else {
            this[to] = item
        }
    }

    private fun GridDimensions.indexOf(cell: GridCell): Int? =
        cell
            .takeIf { checkedCell ->
                checkedCell.column in 0 until columns &&
                    checkedCell.row in 0 until rows
            }
            ?.let { checkedCell -> checkedCell.row * columns + checkedCell.column }

    private fun GridDimensions.cellAt(index: Int): GridCell? =
        index
            .takeIf { checkedIndex -> checkedIndex in 0 until (columns * rows) }
            ?.let { checkedIndex ->
                GridCell(
                    column = checkedIndex % columns,
                    row = checkedIndex / columns,
                )
            }

    private data class AnchorShiftRequest(
        val originIndex: Int,
        val targetIndex: Int,
    )
}

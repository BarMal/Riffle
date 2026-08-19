package com.riffle.core.domain.launcher.home

data class GridDimensions(
    val columns: Int,
    val rows: Int,
) {
    val cellCount: Int = columns * rows
}

data class GridCell(
    val column: Int,
    val row: Int,
)

data class GridSpan(
    val columns: Int = 1,
    val rows: Int = 1,
)

data class GridPlacement(
    val cell: GridCell,
    val span: GridSpan = GridSpan(),
)

/** Whether [placement] lies wholly inside this grid. */
fun GridDimensions.holds(placement: GridPlacement?): Boolean =
    placement != null &&
        placement.cell.column >= 0 &&
        placement.cell.row >= 0 &&
        placement.cell.column + placement.span.columns <= columns &&
        placement.cell.row + placement.span.rows <= rows

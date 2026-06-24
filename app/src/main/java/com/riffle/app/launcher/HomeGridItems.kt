package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage

fun LauncherPage.itemAt(cell: GridCell): LauncherItem? = items.itemAt(cell = cell)

fun List<LauncherItem>.itemAt(cell: GridCell): LauncherItem? = firstOrNull { item -> item.placement?.cell == cell }

fun List<LauncherItem>.occupyingItemAt(cell: GridCell): LauncherItem? =
    firstOrNull { item ->
        item.occupies(cell = cell)
    }

fun LauncherItem.occupies(cell: GridCell): Boolean {
    val placement = placement ?: return false
    val columnRange = placement.cell.column until placement.cell.column + placement.span.columns.coerceAtLeast(1)
    val rowRange = placement.cell.row until placement.cell.row + placement.span.rows.coerceAtLeast(1)

    return cell.column in columnRange && cell.row in rowRange
}

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherPage.previewCells(): List<PagePreviewCell> =
    previewItems()
        .flatMap { item -> item.previewCells() }
        .distinctBy { previewCell -> previewCell.cell }
        .sortedWith(
            compareBy<PagePreviewCell> { previewCell -> previewCell.cell.row }
                .thenBy { previewCell -> previewCell.cell.column },
        )

internal fun LauncherPage.previewItems(): List<PagePreviewItem> = items.mapNotNull { item -> item.previewItemIn(this) }

internal data class PagePreviewCell(
    val cell: GridCell,
    val kind: PagePreviewCellKind,
)

internal data class PagePreviewItem(
    val item: LauncherItem,
    val cell: GridCell,
    val columns: Int,
    val rows: Int,
) {
    val kind: PagePreviewCellKind = item.previewCellKind
}

internal enum class PagePreviewCellKind {
    APP,
    FOLDER,
    WIDGET,
}

private fun LauncherItem.previewItemIn(page: LauncherPage): PagePreviewItem? {
    val placement = placement ?: return null
    val clippedStartColumn = placement.cell.column.coerceAtLeast(0)
    val clippedStartRow = placement.cell.row.coerceAtLeast(0)
    val clippedEndColumn = (placement.cell.column + placement.span.columns).coerceAtMost(page.grid.columns)
    val clippedEndRow = (placement.cell.row + placement.span.rows).coerceAtMost(page.grid.rows)
    val clippedColumns = clippedEndColumn - clippedStartColumn
    val clippedRows = clippedEndRow - clippedStartRow

    return when {
        clippedColumns <= 0 || clippedRows <= 0 -> null
        else ->
            PagePreviewItem(
                item = this,
                cell = GridCell(column = clippedStartColumn, row = clippedStartRow),
                columns = clippedColumns,
                rows = clippedRows,
            )
    }
}

private fun PagePreviewItem.previewCells(): List<PagePreviewCell> =
    GridPlacement(
        cell = cell,
        span = GridSpan(columns = columns, rows = rows),
    ).coveredCells()
        .map { cell ->
            PagePreviewCell(
                cell = cell,
                kind = kind,
            )
        }

private fun GridPlacement.coveredCells(): List<GridCell> =
    (cell.row until cell.row + span.rows).flatMap { row ->
        (cell.column until cell.column + span.columns).map { column ->
            GridCell(column = column, row = row)
        }
    }

private val LauncherItem.previewCellKind: PagePreviewCellKind
    get() =
        when (this) {
            is AppShortcutItem -> PagePreviewCellKind.APP
            is FolderItem -> PagePreviewCellKind.FOLDER
            is WidgetItem -> PagePreviewCellKind.WIDGET
        }

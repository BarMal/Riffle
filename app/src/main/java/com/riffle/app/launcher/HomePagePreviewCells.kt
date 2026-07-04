package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherPage.previewCells(): List<PagePreviewCell> =
    items
        .flatMap { item -> item.previewCellsIn(this) }
        .distinctBy { previewCell -> previewCell.cell }
        .sortedWith(
            compareBy<PagePreviewCell> { previewCell -> previewCell.cell.row }
                .thenBy { previewCell -> previewCell.cell.column },
        )

internal data class PagePreviewCell(
    val cell: GridCell,
    val kind: PagePreviewCellKind,
)

internal enum class PagePreviewCellKind {
    APP,
    FOLDER,
    WIDGET,
}

private fun LauncherItem.previewCellsIn(page: LauncherPage): List<PagePreviewCell> {
    val placement = placement ?: return emptyList()

    return placement.coveredCells()
        .filter { cell -> cell.isInGrid(page) }
        .map { cell ->
            PagePreviewCell(
                cell = cell,
                kind = previewCellKind,
            )
        }
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

private fun GridCell.isInGrid(page: LauncherPage): Boolean =
    column in 0 until page.grid.columns &&
        row in 0 until page.grid.rows

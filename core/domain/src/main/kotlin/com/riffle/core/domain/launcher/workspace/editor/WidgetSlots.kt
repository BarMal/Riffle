package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.container.WidgetGridPlanner

data class WidgetSlot(
    val column: Int,
    val row: Int,
)

/** Finds free positions in a widget grid with the same placement engine the page grid uses. */
object WidgetSlots {
    const val DEFAULT_COLUMNS = 4
    const val DEFAULT_ROWS = 6

    private val engine = GridPlacementEngine()

    /** The first free position (reading order) where a widget of [span] fits, or null when the grid is full. */
    fun firstFree(
        grid: PageContent.WidgetGrid,
        span: WidgetSpan,
    ): WidgetSlot? {
        val occupied = WidgetGridPlanner.plan(grid).widgets
        var page = LauncherPage(id = LauncherPageId("editor"), grid = GridDimensions(grid.columns, grid.rows))
        occupied.forEachIndexed { index, planned ->
            val item =
                WidgetItem(
                    id = LauncherItemId(planned.key),
                    appWidgetId = HostedWidgetId(index),
                    label = planned.key,
                    placement =
                        GridPlacement(
                            GridCell(planned.column, planned.row),
                            GridSpan(planned.widget.span.columns, planned.widget.span.rows),
                        ),
                )
            page = (engine.placeItem(page, item) as? PlaceLauncherItemResult.Placed)?.page ?: page
        }
        val probe =
            WidgetItem(
                id = LauncherItemId(PROBE_ID),
                appWidgetId = HostedWidgetId(occupied.size),
                label = PROBE_ID,
            )
        val result = engine.placeItemInFirstAvailableCell(page, probe, GridSpan(span.columns, span.rows))
        val placement = (result as? PlaceLauncherItemResult.Placed)?.page?.items?.lastOrNull()?.placement
        return placement?.let { WidgetSlot(it.cell.column, it.cell.row) }
    }

    /** A sensible starting size for [kind] in a grid of [columns] x [rows], never larger than the grid. */
    fun defaultSpan(
        kind: ExpressionKind,
        columns: Int = DEFAULT_COLUMNS,
        rows: Int = DEFAULT_ROWS,
    ): WidgetSpan {
        val (wide, tall) =
            when (kind) {
                ExpressionKind.ICON_ROW -> columns to 1
                ExpressionKind.CARD -> columns / 2 to 2
                ExpressionKind.ICON_GRID, ExpressionKind.LIST -> columns to 2
                ExpressionKind.INDEX,
                ExpressionKind.CARD_STACK,
                ExpressionKind.CATEGORIES,
                ExpressionKind.ALPHA_LIST,
                -> columns to 3
            }
        return WidgetSpan(wide.coerceIn(1, columns), tall.coerceIn(1, rows))
    }

    private const val PROBE_ID = "editor.free-slot-probe"
}

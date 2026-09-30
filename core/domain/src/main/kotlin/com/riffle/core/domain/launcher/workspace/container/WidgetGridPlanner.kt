package com.riffle.core.domain.launcher.workspace.container

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
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WidgetContainer

enum class WidgetDropReason {
    OUT_OF_BOUNDS,
    COLLISION,
    DUPLICATE_ID,
    TOO_MANY,
}

data class PlannedWidget(
    val widget: WidgetContainer,
    val column: Int,
    val row: Int,
) {
    /** Stable composition key. */
    val key: String get() = widget.id.value
}

data class DroppedWidget(
    val widgetId: ContainerId,
    val reason: WidgetDropReason,
)

/**
 * The widgets a grid page draws, in reading order (top to bottom, then left to right; also the
 * accessibility traversal order), none overlapping and all inside the grid.
 */
data class WidgetGridPlan(
    val columns: Int,
    val rows: Int,
    val widgets: List<PlannedWidget>,
    val dropped: List<DroppedWidget> = emptyList(),
)

/** A rectangle in the host's unit (px or dp); the planner never assumes which. */
data class CellRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Turns a stored [PageContent.WidgetGrid] into something safe to draw. Stored grids are validated by
 * `ContainerValidation`, but a grid can still arrive broken (a migration bug, a newer version's data,
 * a hand-edited backup); drawing must not crash or overlap, so this tolerates it: the first placement
 * in stored order wins and later offenders are dropped and reported. Collision and bounds decisions
 * come from [GridPlacementEngine] so the page grid and workspace grids cannot disagree.
 */
object WidgetGridPlanner {
    /** Composition bound: no grid draws more widgets than this. */
    const val MAX_WIDGETS = 48

    private val engine = GridPlacementEngine()

    fun plan(grid: PageContent.WidgetGrid): WidgetGridPlan {
        var page = LauncherPage(id = LauncherPageId("container"), grid = GridDimensions(grid.columns, grid.rows))
        val accepted = ArrayList<PlannedWidget>()
        val dropped = ArrayList<DroppedWidget>()
        grid.placements.forEachIndexed { index, placement ->
            val widget = placement.widget
            if (accepted.size >= MAX_WIDGETS) {
                dropped += DroppedWidget(widget.id, WidgetDropReason.TOO_MANY)
                return@forEachIndexed
            }
            val item =
                WidgetItem(
                    id = LauncherItemId(widget.id.value),
                    appWidgetId = HostedWidgetId(index),
                    label = widget.id.value,
                    placement =
                        GridPlacement(
                            GridCell(placement.column, placement.row),
                            GridSpan(widget.span.columns, widget.span.rows),
                        ),
                )
            when (val result = engine.placeItem(page, item)) {
                is PlaceLauncherItemResult.Placed -> {
                    page = result.page
                    accepted += PlannedWidget(widget, placement.column, placement.row)
                }
                is PlaceLauncherItemResult.Rejected -> dropped += DroppedWidget(widget.id, result.reason.toDrop())
            }
        }
        val ordered = accepted.sortedWith(compareBy<PlannedWidget> { it.row }.thenBy { it.column })
        return WidgetGridPlan(grid.columns, grid.rows, ordered, dropped)
    }

    private fun PlacementRejectionReason.toDrop(): WidgetDropReason =
        when (this) {
            PlacementRejectionReason.COLLISION -> WidgetDropReason.COLLISION
            PlacementRejectionReason.DUPLICATE_ITEM_ID -> WidgetDropReason.DUPLICATE_ID
            else -> WidgetDropReason.OUT_OF_BOUNDS
        }

    /**
     * Where [planned] sits in a grid area of [width] x [height] with [gap] between cells. Cells divide the
     * area evenly; a span covers its cells plus the gaps between them, so neighbours never overlap.
     */
    fun rectFor(
        plan: WidgetGridPlan,
        planned: PlannedWidget,
        width: Float,
        height: Float,
        gap: Float = 0f,
    ): CellRect {
        val cellWidth = ((width - gap * (plan.columns - 1)) / plan.columns).coerceAtLeast(0f)
        val cellHeight = ((height - gap * (plan.rows - 1)) / plan.rows).coerceAtLeast(0f)
        val spanColumns = planned.widget.span.columns
        val spanRows = planned.widget.span.rows
        return CellRect(
            left = planned.column * (cellWidth + gap),
            top = planned.row * (cellHeight + gap),
            width = cellWidth * spanColumns + gap * (spanColumns - 1),
            height = cellHeight * spanRows + gap * (spanRows - 1),
        )
    }
}

package com.riffle.core.domain.launcher.home

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The numbers of a Home drag (Settings > About > Developer options > "Drag tuning"): radii are in cells (the smaller
 * dimension of a cell), times in milliseconds. [coerced] holds each to its range.
 */
data class HomeDragTuning(
    /** Outer radius of the push ring around the dragged item's centre. */
    val ringRadius: Float = DEFAULT_RING_RADIUS,
    /** Radius of the dead zone at an app's cell centre: dropping there makes a folder instead of pushing. */
    val deadZoneRadius: Float = DEFAULT_DEAD_ZONE_RADIUS,
    /** How long the dragged item holds in a dead zone before the "will make a folder" state shows. */
    val folderHoldMillis: Int = DEFAULT_FOLDER_HOLD_MILLIS,
    /** How long the dragged item holds near a screen edge before the page turns. */
    val edgeHoldMillis: Int = DEFAULT_EDGE_HOLD_MILLIS,
    /** How often the page keeps turning while the hold continues. */
    val pageRepeatMillis: Int = DEFAULT_PAGE_REPEAT_MILLIS,
) {
    fun coerced(): HomeDragTuning =
        HomeDragTuning(
            ringRadius = ringRadius.coerceIn(RING_RANGE),
            deadZoneRadius = deadZoneRadius.coerceIn(DEAD_ZONE_RANGE),
            folderHoldMillis = folderHoldMillis.coerceIn(FOLDER_HOLD_RANGE),
            edgeHoldMillis = edgeHoldMillis.coerceIn(EDGE_HOLD_RANGE),
            pageRepeatMillis = pageRepeatMillis.coerceIn(PAGE_REPEAT_RANGE),
        )

    /** The values as text the owner can copy. */
    fun asText(): String =
        "ring=$ringRadius dead=$deadZoneRadius folderHold=${folderHoldMillis}ms " +
            "edgeHold=${edgeHoldMillis}ms pageRepeat=${pageRepeatMillis}ms"

    companion object {
        const val DEFAULT_RING_RADIUS = 0.75f
        const val DEFAULT_DEAD_ZONE_RADIUS = 0.3f
        const val DEFAULT_FOLDER_HOLD_MILLIS = 250
        const val DEFAULT_EDGE_HOLD_MILLIS = 500
        const val DEFAULT_PAGE_REPEAT_MILLIS = 700
        val RING_RANGE = 0.5f..1.2f
        val DEAD_ZONE_RANGE = 0.15f..0.45f
        val FOLDER_HOLD_RANGE = 100..600
        val EDGE_HOLD_RANGE = 200..1000
        val PAGE_REPEAT_RANGE = 300..1500

        /** The distance from a screen edge that counts as holding at the edge, in dp. */
        const val EDGE_ZONE_DP = 24

        /** The extra ring an item already pushed keeps, so it does not jitter at the boundary (in cells). */
        const val HYSTERESIS = 0.12f
    }
}

/**
 * What a drag over a page would do right now. [landing] is where the dragged item would go (null: nowhere valid, so a
 * drop cancels and the item returns). [pushed] are the items that move out of the way, to the cell they would take;
 * nothing is committed until the drop. [dropOn] is the app or folder whose dead zone the pointer is in (a drop there
 * makes or fills a folder, or makes or extends a stack; [landing] is then that item's own cell and [pushed] is empty).
 */
data class HomeDragPlan(
    val landing: GridCell?,
    val pushed: Map<LauncherItemId, GridCell> = emptyMap(),
    val dropOn: LauncherItemId? = null,
)

/**
 * "Repellent magnetism" (the owner's words): around the dragged item's centre there is a tight ring; an item whose
 * cell centre is inside the ring but outside its own dead zone is pushed to the nearest free cell away from the
 * dragged item, so the item can shunt others aside and relayout the grid. The dead zone (an app's cell centre, a
 * whole folder icon) is where a drop lands on top. Pure; never throws; nothing here mutates the page.
 *
 * Rules, in the order they apply:
 * 1. A dragged app inside another app's dead zone (or over a folder's icon), or a dragged widget inside the dead zone of
 *    a widget or stack of the same span: [HomeDragPlan.dropOn], nothing pushed.
 * 2. Otherwise every other item whose centre (a widget: the nearest point of its span) is inside the ring is pushed,
 *    closest first, plus every item that stands where the dragged item would land. An item already pushed by the
 *    previous plan stays pushed out to the ring plus [HomeDragTuning.HYSTERESIS] (no jitter at the boundary).
 * 3. A pushed item takes the nearest free cell in the direction away from the pointer (stepping along the dominant
 *    axis, then the other, then any free cell by distance, in reading order). Whole spans only; never off the page.
 *    The page is not extended: a full page refuses the push (the item stays and blocks), and if the blocker stands on
 *    the landing cell the plan has no landing (the existing placement rules reject a collision the same way).
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod", "ComplexCondition") // One small geometric rule.
object HomeDragPush {
    fun plan(
        page: LauncherPage,
        dragged: LauncherItem,
        x: Float,
        y: Float,
        tuning: HomeDragTuning = HomeDragTuning(),
        pushedBefore: Set<LauncherItemId> = emptySet(),
        /** Cells nothing may be pushed into (the cell the dragged item is lifted from, which stays on screen). */
        reserved: Set<GridCell> = emptySet(),
    ): HomeDragPlan {
        val grid = page.grid
        val span = dragged.placement?.span ?: GridSpan()
        if (grid.columns <= 0 || grid.rows <= 0 || span.columns > grid.columns || span.rows > grid.rows) {
            return HomeDragPlan(landing = null)
        }
        val others = page.items.filter { it.id != dragged.id && it.placement != null }
        val landing =
            GridCell(
                column = floor(x).toInt().coerceIn(0, grid.columns - span.columns),
                row = floor(y).toInt().coerceIn(0, grid.rows - span.rows),
            )
        val target = deadZoneTarget(others, dragged, x, y, tuning)
        if (target != null) return HomeDragPlan(landing = target.placement!!.cell, dropOn = target.id)

        val landingCells = GridPlacement(landing, span).cells()
        val pushing =
            others.filter { item ->
                val radius = tuning.ringRadius + if (item.id in pushedBefore) HomeDragTuning.HYSTERESIS else 0f
                distanceTo(item, x, y) <= radius || item.cells().any { it in landingCells }
            }.sortedBy { distanceTo(it, x, y) }
        val occupied = others.filter { it !in pushing }.flatMap { it.cells() }.toMutableSet()
        occupied += landingCells
        occupied += reserved
        val moved = LinkedHashMap<LauncherItemId, GridCell>()
        for (item in pushing) {
            val cell = freeCellAway(item, grid, occupied, x, y)
            if (cell == null) {
                // Blocked: it stays and blocks; if it stands on the landing there is nowhere valid to drop.
                if (item.cells().any { it in landingCells }) return HomeDragPlan(landing = null)
                occupied += item.cells()
            } else {
                moved[item.id] = cell
                occupied += GridPlacement(cell, item.placement!!.span).cells()
            }
        }
        return HomeDragPlan(landing = landing, pushed = moved)
    }

    private fun deadZoneTarget(
        others: List<LauncherItem>,
        dragged: LauncherItem,
        x: Float,
        y: Float,
        tuning: HomeDragTuning,
    ): LauncherItem? {
        if (dragged is WidgetItem) return stackTarget(others, dragged, x, y, tuning)
        if (dragged !is AppShortcutItem) return null
        return others.firstOrNull { item ->
            val cell = item.placement?.cell ?: return@firstOrNull false
            when (item) {
                is AppShortcutItem -> distanceTo(item, x, y) <= tuning.deadZoneRadius
                // A folder's dead zone is its whole icon (its cell).
                is FolderItem -> abs(x - (cell.column + CENTRE)) <= CENTRE && abs(y - (cell.row + CENTRE)) <= CENTRE
                is WidgetItem -> false
            }
        }
    }

    /**
     * The widget of the same span whose dead zone the pointer is in: its span inset by the dead-zone radius on every side
     * (the centre of its area, as an app's dead zone is the centre of its cell), so a drag only passing over it still
     * pushes it. A widget of another span never stacks; it is pushed as before.
     */
    private fun stackTarget(
        others: List<LauncherItem>,
        dragged: WidgetItem,
        x: Float,
        y: Float,
        tuning: HomeDragTuning,
    ): LauncherItem? =
        others.firstOrNull { item ->
            val placement = item.placement
            item is WidgetItem && placement != null && stacks.canStack(item, dragged) &&
                abs(x - (placement.cell.column + placement.span.columns / 2f)) <=
                maxOf(placement.span.columns / 2f - tuning.deadZoneRadius, tuning.deadZoneRadius) &&
                abs(y - (placement.cell.row + placement.span.rows / 2f)) <=
                maxOf(placement.span.rows / 2f - tuning.deadZoneRadius, tuning.deadZoneRadius)
        }

    private val stacks = WidgetStackEngine()

    private fun freeCellAway(
        item: LauncherItem,
        grid: GridDimensions,
        occupied: Set<GridCell>,
        x: Float,
        y: Float,
    ): GridCell? {
        val placement = item.placement ?: return null
        val centreX = placement.cell.column + placement.span.columns / 2f
        val centreY = placement.cell.row + placement.span.rows / 2f
        val dx = centreX - x
        val dy = centreY - y
        val horizontal = abs(dx) >= abs(dy)
        val primary = if (horizontal) sign(dx) to 0 else 0 to sign(dy)
        val secondary = if (horizontal) 0 to sign(dy) else sign(dx) to 0

        fun fits(cell: GridCell) =
            cell.column >= 0 && cell.row >= 0 &&
                cell.column + placement.span.columns <= grid.columns &&
                cell.row + placement.span.rows <= grid.rows &&
                GridPlacement(cell, placement.span).cells().none { it in occupied }
        val here = placement.cell
        val lines =
            listOf(primary, secondary, -primary.first to -primary.second, -secondary.first to -secondary.second)
        for ((stepX, stepY) in lines) {
            if (stepX == 0 && stepY == 0) continue
            for (n in 1..maxOf(grid.columns, grid.rows)) {
                val cell = GridCell(here.column + stepX * n, here.row + stepY * n)
                if (fits(cell)) return cell
            }
        }
        return (0 until grid.rows).flatMap { row -> (0 until grid.columns).map { GridCell(it, row) } }
            .filter { it != here && fits(it) }
            .minByOrNull { hypot((it.column - here.column).toFloat(), (it.row - here.row).toFloat()) }
    }

    private fun sign(value: Float): Int =
        if (value > 0f) {
            1
        } else if (value < 0f) {
            -1
        } else {
            1
        }

    private operator fun Pair<Int, Int>.unaryMinus(): Pair<Int, Int> = -first to -second

    /** Distance in cells from the pointer to the item's centre; a span item: to the nearest point of its span. */
    private fun distanceTo(
        item: LauncherItem,
        x: Float,
        y: Float,
    ): Float {
        val placement = item.placement ?: return Float.MAX_VALUE
        if (placement.span.columns == 1 && placement.span.rows == 1) {
            return hypot(placement.cell.column + CENTRE - x, placement.cell.row + CENTRE - y)
        }
        val nearestX =
            x.coerceIn(
                placement.cell.column.toFloat(),
                (placement.cell.column + placement.span.columns).toFloat(),
            )
        val nearestY = y.coerceIn(placement.cell.row.toFloat(), (placement.cell.row + placement.span.rows).toFloat())
        return hypot(nearestX - x, nearestY - y)
    }

    private fun LauncherItem.cells(): Set<GridCell> = placement?.cells().orEmpty()

    private fun GridPlacement.cells(): Set<GridCell> =
        (cell.column until cell.column + span.columns.coerceAtLeast(1))
            .flatMap { column -> (cell.row until cell.row + span.rows.coerceAtLeast(1)).map { GridCell(column, it) } }
            .toSet()

    private const val CENTRE = 0.5f
}

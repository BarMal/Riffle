package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

@JvmInline
value class TimeScapeTemplateId(val value: String)

/** A bounded, framework-independent description of a TimeScape home surface. */
data class TimeScapeTemplate(
    val id: TimeScapeTemplateId,
    val displayName: String,
    val description: String,
    val variants: List<TimeScapeTemplateVariant>,
)

data class TimeScapeTemplateVariant(
    val deviceClass: HomeLayoutDeviceClass,
    val paneMode: TimeScapePaneMode,
    val canvas: TimeScapeCanvas,
    val dynamicSlots: List<TimeScapeDynamicSlot>,
)

data class TimeScapeCanvas(
    val grid: GridDimensions,
    val elements: List<TimeScapeStaticElement>,
)

@JvmInline
value class TimeScapeElementId(val value: String)

data class TimeScapeStaticElement(
    val id: TimeScapeElementId,
    val type: TimeScapeStaticElementType,
    val placement: GridPlacement,
    val visibleInCompact: Boolean = true,
    val visibleInStageManager: Boolean = true,
)

enum class TimeScapeStaticElementType {
    CLOCK,
    SEARCH,
    APP_CAROUSEL,
    DOCK,
    IMAGE,
    SHAPE,
    WIDGET,
}

@JvmInline
value class TimeScapeDynamicSlotId(val value: String)

data class TimeScapeDynamicSlot(
    val id: TimeScapeDynamicSlotId,
    val source: TimeScapeDynamicSource,
    val placement: GridPlacement,
)

enum class TimeScapeDynamicSource {
    APP_STAGE_STACKS,
    PINNED_APP_PAGE_STACKS,
    RECENT_APP_STAGES,
    FUTURE_FEED,
}

fun TimeScapeTemplate.variantFor(
    deviceClass: HomeLayoutDeviceClass,
    paneMode: TimeScapePaneMode,
): TimeScapeTemplateVariant? =
    variants.firstOrNull { variant -> variant.deviceClass == deviceClass && variant.paneMode == paneMode }
        ?: variants.firstOrNull { variant ->
            variant.deviceClass == deviceClass && variant.paneMode == TimeScapePaneMode.COMPACT
        }

fun TimeScapeTemplateVariant.visibleStaticElements(): List<TimeScapeStaticElement> =
    canvas.elements.filter { element ->
        when (paneMode) {
            TimeScapePaneMode.COMPACT -> element.visibleInCompact
            TimeScapePaneMode.TWO_PANE, TimeScapePaneMode.THREE_PANE -> element.visibleInStageManager
        }
    }

sealed interface TimeScapeTemplateValidationIssue {
    data class InvalidGrid(val grid: GridDimensions) : TimeScapeTemplateValidationIssue

    data class BlankElementId(val id: TimeScapeElementId) : TimeScapeTemplateValidationIssue

    data class BlankSlotId(val id: TimeScapeDynamicSlotId) : TimeScapeTemplateValidationIssue

    data class DuplicateElementId(val id: TimeScapeElementId) : TimeScapeTemplateValidationIssue

    data class DuplicateSlotId(val id: TimeScapeDynamicSlotId) : TimeScapeTemplateValidationIssue

    data class OutOfBounds(val placement: GridPlacement) : TimeScapeTemplateValidationIssue

    data class Collision(val placement: GridPlacement) : TimeScapeTemplateValidationIssue
}

fun TimeScapeTemplateVariant.validate(): List<TimeScapeTemplateValidationIssue> {
    val issues = mutableListOf<TimeScapeTemplateValidationIssue>()
    if (canvas.grid.columns <= 0 || canvas.grid.rows <= 0) {
        issues += TimeScapeTemplateValidationIssue.InvalidGrid(canvas.grid)
    }
    val elementIds = canvas.elements.map { element -> element.id }
    elementIds.filter { id -> id.value.isBlank() }.forEach { id ->
        issues += TimeScapeTemplateValidationIssue.BlankElementId(id)
    }
    elementIds.groupingBy { id -> id }.eachCount().filterValues { count -> count > 1 }.keys.forEach { id ->
        issues += TimeScapeTemplateValidationIssue.DuplicateElementId(id)
    }
    val slotIds = dynamicSlots.map { slot -> slot.id }
    slotIds.filter { id -> id.value.isBlank() }.forEach { id ->
        issues += TimeScapeTemplateValidationIssue.BlankSlotId(id)
    }
    slotIds.groupingBy { id -> id }.eachCount().filterValues { count -> count > 1 }.keys.forEach { id ->
        issues += TimeScapeTemplateValidationIssue.DuplicateSlotId(id)
    }
    val placements = canvas.elements.map { element -> element.placement } + dynamicSlots.map { slot -> slot.placement }
    val occupied = mutableSetOf<GridCell>()
    placements.forEach { placement ->
        val cells = placement.cells()
        if (cells.any(::isOutsideGrid)) {
            issues += TimeScapeTemplateValidationIssue.OutOfBounds(placement)
        }
        if (!occupied.addAll(cells)) {
            issues += TimeScapeTemplateValidationIssue.Collision(placement)
        }
    }
    return issues
}

private fun TimeScapeTemplateVariant.isOutsideGrid(cell: GridCell): Boolean =
    cell.column < 0 ||
        cell.row < 0 ||
        cell.column >= canvas.grid.columns ||
        cell.row >= canvas.grid.rows

private fun GridPlacement.cells(): Set<GridCell> =
    (cell.column until cell.column + span.columns.coerceAtLeast(1)).flatMap { column ->
        (cell.row until cell.row + span.rows.coerceAtLeast(1)).map { row -> GridCell(column, row) }
    }.toSet()

object TimeScapeTemplateCatalogDefaults {
    val sharedCanvasId = TimeScapeTemplateId("timescape-shared-canvas")

    val sharedCanvas: TimeScapeTemplate =
        TimeScapeTemplate(
            id = sharedCanvasId,
            displayName = "Shared canvas",
            description = "A persistent canvas around app-stage cards.",
            variants =
                listOf(
                    variant(HomeLayoutDeviceClass.PHONE, 4, 6),
                    variant(HomeLayoutDeviceClass.FOLDABLE, 8, 6),
                ),
        )

    val templates: List<TimeScapeTemplate> = listOf(sharedCanvas)

    private fun variant(
        deviceClass: HomeLayoutDeviceClass,
        columns: Int,
        rows: Int,
    ): TimeScapeTemplateVariant =
        TimeScapeTemplateVariant(
            deviceClass = deviceClass,
            paneMode = TimeScapePaneMode.COMPACT,
            canvas =
                TimeScapeCanvas(
                    grid = GridDimensions(columns, rows),
                    elements =
                        listOf(
                            staticElement("clock", TimeScapeStaticElementType.CLOCK, 0, 0, columns, 1),
                            staticElement("carousel", TimeScapeStaticElementType.APP_CAROUSEL, 0, 1, columns, 1),
                            staticElement("dock", TimeScapeStaticElementType.DOCK, 0, rows - 1, columns, 1),
                        ),
                ),
            dynamicSlots =
                listOf(
                    TimeScapeDynamicSlot(
                        id = TimeScapeDynamicSlotId("app-stage"),
                        source = TimeScapeDynamicSource.APP_STAGE_STACKS,
                        placement = GridPlacement(GridCell(0, 2), GridSpan(columns, rows - 3)),
                    ),
                ),
        )

    private fun staticElement(
        id: String,
        type: TimeScapeStaticElementType,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
    ): TimeScapeStaticElement =
        TimeScapeStaticElement(
            id = TimeScapeElementId(id),
            type = type,
            placement = GridPlacement(GridCell(column, row), GridSpan(columns, rows)),
        )
}

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
    val railSide: TimeScapeRailSide = TimeScapeRailSide.LEADING,
)

enum class TimeScapeRailSide {
    LEADING,
    TRAILING,
}

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
            // SPLIT has no authored template variants yet -- variantFor() already falls back to
            // the COMPACT variant for it, so this branch mirrors that same compact visibility for
            // the (currently unreachable) case where a variant's own paneMode is SPLIT.
            TimeScapePaneMode.COMPACT, TimeScapePaneMode.SPLIT -> element.visibleInCompact
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

    data class InvalidSpan(val placement: GridPlacement) : TimeScapeTemplateValidationIssue

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
    val placements =
        canvas.elements.map { element -> TaggedPlacement(element.placement, isDynamicSlot = false) } +
            dynamicSlots.map { slot -> TaggedPlacement(slot.placement, isDynamicSlot = true) }
    val acceptedPlacements = mutableListOf<TaggedPlacement>()
    placements.forEach { tagged ->
        when (val placementIssue = tagged.placement.validationIssue(canvas.grid)) {
            null -> {
                // Dynamic slots may intentionally share a region: they represent alternative
                // content sources (e.g. app-stage stacks and feed stages) that coexist within the
                // same visual stage area rather than separate canvas regions. Only a static
                // element colliding with anything else is a real layout conflict.
                val collides =
                    acceptedPlacements.any { accepted ->
                        val bothDynamic = accepted.isDynamicSlot && tagged.isDynamicSlot
                        !bothDynamic && accepted.placement.overlaps(tagged.placement)
                    }
                if (collides) {
                    issues += TimeScapeTemplateValidationIssue.Collision(tagged.placement)
                }
                acceptedPlacements += tagged
            }

            else -> issues += placementIssue
        }
    }
    return issues
}

private data class TaggedPlacement(val placement: GridPlacement, val isDynamicSlot: Boolean)

private fun GridPlacement.validationIssue(grid: GridDimensions): TimeScapeTemplateValidationIssue? {
    if (span.columns <= 0 || span.rows <= 0) {
        return TimeScapeTemplateValidationIssue.InvalidSpan(this)
    }
    val rightExclusive = cell.column.toLong() + span.columns.toLong()
    val bottomExclusive = cell.row.toLong() + span.rows.toLong()
    val startsOutside = cell.column < 0 || cell.row < 0
    val endsOutside = rightExclusive > grid.columns.toLong() || bottomExclusive > grid.rows.toLong()
    return if (startsOutside || endsOutside) {
        TimeScapeTemplateValidationIssue.OutOfBounds(this)
    } else {
        null
    }
}

private fun GridPlacement.overlaps(other: GridPlacement): Boolean {
    val rightExclusive = cell.column.toLong() + span.columns.toLong()
    val bottomExclusive = cell.row.toLong() + span.rows.toLong()
    val otherRightExclusive = other.cell.column.toLong() + other.span.columns.toLong()
    val otherBottomExclusive = other.cell.row.toLong() + other.span.rows.toLong()
    return cell.column.toLong() < otherRightExclusive &&
        other.cell.column.toLong() < rightExclusive &&
        cell.row.toLong() < otherBottomExclusive &&
        other.cell.row.toLong() < bottomExclusive
}

object TimeScapeTemplateCatalogDefaults {
    val sharedCanvasId = TimeScapeTemplateId("timescape-shared-canvas")

    val sharedCanvas: TimeScapeTemplate =
        TimeScapeTemplate(
            id = sharedCanvasId,
            displayName = "Shared canvas",
            description = "A persistent canvas around app-stage cards.",
            variants =
                listOf(
                    variant(HomeLayoutDeviceClass.PHONE, TimeScapePaneMode.COMPACT, 4, 7),
                    variant(HomeLayoutDeviceClass.PHONE, TimeScapePaneMode.THREE_PANE, 5, 7),
                    variant(HomeLayoutDeviceClass.FOLDABLE, TimeScapePaneMode.COMPACT, 8, 7),
                    variant(HomeLayoutDeviceClass.FOLDABLE, TimeScapePaneMode.THREE_PANE, 10, 7),
                ),
        )

    val templates: List<TimeScapeTemplate> = listOf(sharedCanvas)

    private fun variant(
        deviceClass: HomeLayoutDeviceClass,
        paneMode: TimeScapePaneMode,
        columns: Int,
        rows: Int,
    ): TimeScapeTemplateVariant =
        TimeScapeTemplateVariant(
            deviceClass = deviceClass,
            paneMode = paneMode,
            canvas =
                TimeScapeCanvas(
                    grid = GridDimensions(columns, rows),
                    elements =
                        listOf(
                            staticElement("clock", TimeScapeStaticElementType.CLOCK, 0, 0, columns, 1),
                            staticElement("search", TimeScapeStaticElementType.SEARCH, 0, 1, columns, 1),
                            staticElement("carousel", TimeScapeStaticElementType.APP_CAROUSEL, 0, 2, columns, 1),
                            staticElement("dock", TimeScapeStaticElementType.DOCK, 0, rows - 1, columns, 1),
                        ),
                ),
            dynamicSlots =
                listOf(
                    TimeScapeDynamicSlot(
                        id = TimeScapeDynamicSlotId("app-stage"),
                        source = TimeScapeDynamicSource.APP_STAGE_STACKS,
                        placement = GridPlacement(GridCell(0, 3), GridSpan(columns, rows - 4)),
                    ),
                    // Feed stages coexist with app stages in the same stage area rather than a
                    // separate canvas region (see ADR 0001, "Template binding and coexistence").
                    TimeScapeDynamicSlot(
                        id = TimeScapeDynamicSlotId("feed-stage"),
                        source = TimeScapeDynamicSource.FUTURE_FEED,
                        placement = GridPlacement(GridCell(0, 3), GridSpan(columns, rows - 4)),
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

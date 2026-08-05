package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

@JvmInline
value class AdaptiveStageTemplateId(val value: String)

/** A bounded, framework-independent description of a AdaptiveStage home surface. */
data class AdaptiveStageTemplate(
    val id: AdaptiveStageTemplateId,
    val displayName: String,
    val description: String,
    val variants: List<AdaptiveStageTemplateVariant>,
)

data class AdaptiveStageTemplateVariant(
    val deviceClass: HomeLayoutDeviceClass,
    val paneMode: AdaptiveStagePaneMode,
    val canvas: AdaptiveStageCanvas,
    val dynamicSlots: List<AdaptiveStageDynamicSlot>,
    val railSide: AdaptiveStageRailSide = AdaptiveStageRailSide.LEADING,
)

enum class AdaptiveStageRailSide {
    LEADING,
    TRAILING,
    TOP,
    BOTTOM,
}

/** True for the two edges where the rail runs as a horizontal strip instead of a side column. */
val AdaptiveStageRailSide.isHorizontalEdge: Boolean
    get() = this == AdaptiveStageRailSide.TOP || this == AdaptiveStageRailSide.BOTTOM

data class AdaptiveStageCanvas(
    val grid: GridDimensions,
    val elements: List<AdaptiveStageStaticElement>,
)

@JvmInline
value class AdaptiveStageElementId(val value: String)

data class AdaptiveStageStaticElement(
    val id: AdaptiveStageElementId,
    val type: AdaptiveStageStaticElementType,
    val placement: GridPlacement,
    val visibleInCompact: Boolean = true,
    val visibleInStageManager: Boolean = true,
)

enum class AdaptiveStageStaticElementType {
    CLOCK,
    SEARCH,
    APP_CAROUSEL,
    DOCK,
    IMAGE,
    SHAPE,
    WIDGET,
}

@JvmInline
value class AdaptiveStageDynamicSlotId(val value: String)

data class AdaptiveStageDynamicSlot(
    val id: AdaptiveStageDynamicSlotId,
    val source: AdaptiveStageDynamicSource,
    val placement: GridPlacement,
)

enum class AdaptiveStageDynamicSource {
    APP_STAGE_STACKS,
    PINNED_APP_PAGE_STACKS,
    RECENT_APP_STAGES,
    FUTURE_FEED,
}

fun AdaptiveStageTemplate.variantFor(
    deviceClass: HomeLayoutDeviceClass,
    paneMode: AdaptiveStagePaneMode,
): AdaptiveStageTemplateVariant? =
    variants.firstOrNull { variant -> variant.deviceClass == deviceClass && variant.paneMode == paneMode }
        ?: variants.firstOrNull { variant ->
            variant.deviceClass == deviceClass && variant.paneMode == AdaptiveStagePaneMode.COMPACT
        }

fun AdaptiveStageTemplateVariant.visibleStaticElements(): List<AdaptiveStageStaticElement> =
    canvas.elements.filter { element ->
        when (paneMode) {
            // SPLIT has no authored template variants yet -- variantFor() already falls back to
            // the COMPACT variant for it, so this branch mirrors that same compact visibility for
            // the (currently unreachable) case where a variant's own paneMode is SPLIT.
            AdaptiveStagePaneMode.COMPACT, AdaptiveStagePaneMode.SPLIT -> element.visibleInCompact
            AdaptiveStagePaneMode.TWO_PANE, AdaptiveStagePaneMode.THREE_PANE -> element.visibleInStageManager
        }
    }

sealed interface AdaptiveStageTemplateValidationIssue {
    data class InvalidGrid(val grid: GridDimensions) : AdaptiveStageTemplateValidationIssue

    data class BlankElementId(val id: AdaptiveStageElementId) : AdaptiveStageTemplateValidationIssue

    data class BlankSlotId(val id: AdaptiveStageDynamicSlotId) : AdaptiveStageTemplateValidationIssue

    data class DuplicateElementId(val id: AdaptiveStageElementId) : AdaptiveStageTemplateValidationIssue

    data class DuplicateSlotId(val id: AdaptiveStageDynamicSlotId) : AdaptiveStageTemplateValidationIssue

    data class OutOfBounds(val placement: GridPlacement) : AdaptiveStageTemplateValidationIssue

    data class InvalidSpan(val placement: GridPlacement) : AdaptiveStageTemplateValidationIssue

    data class Collision(val placement: GridPlacement) : AdaptiveStageTemplateValidationIssue
}

fun AdaptiveStageTemplateVariant.validate(): List<AdaptiveStageTemplateValidationIssue> {
    val issues = mutableListOf<AdaptiveStageTemplateValidationIssue>()
    if (canvas.grid.columns <= 0 || canvas.grid.rows <= 0) {
        issues += AdaptiveStageTemplateValidationIssue.InvalidGrid(canvas.grid)
    }
    val elementIds = canvas.elements.map { element -> element.id }
    elementIds.filter { id -> id.value.isBlank() }.forEach { id ->
        issues += AdaptiveStageTemplateValidationIssue.BlankElementId(id)
    }
    elementIds.groupingBy { id -> id }.eachCount().filterValues { count -> count > 1 }.keys.forEach { id ->
        issues += AdaptiveStageTemplateValidationIssue.DuplicateElementId(id)
    }
    val slotIds = dynamicSlots.map { slot -> slot.id }
    slotIds.filter { id -> id.value.isBlank() }.forEach { id ->
        issues += AdaptiveStageTemplateValidationIssue.BlankSlotId(id)
    }
    slotIds.groupingBy { id -> id }.eachCount().filterValues { count -> count > 1 }.keys.forEach { id ->
        issues += AdaptiveStageTemplateValidationIssue.DuplicateSlotId(id)
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
                    issues += AdaptiveStageTemplateValidationIssue.Collision(tagged.placement)
                }
                acceptedPlacements += tagged
            }

            else -> issues += placementIssue
        }
    }
    return issues
}

private data class TaggedPlacement(val placement: GridPlacement, val isDynamicSlot: Boolean)

private fun GridPlacement.validationIssue(grid: GridDimensions): AdaptiveStageTemplateValidationIssue? {
    if (span.columns <= 0 || span.rows <= 0) {
        return AdaptiveStageTemplateValidationIssue.InvalidSpan(this)
    }
    val rightExclusive = cell.column.toLong() + span.columns.toLong()
    val bottomExclusive = cell.row.toLong() + span.rows.toLong()
    val startsOutside = cell.column < 0 || cell.row < 0
    val endsOutside = rightExclusive > grid.columns.toLong() || bottomExclusive > grid.rows.toLong()
    return if (startsOutside || endsOutside) {
        AdaptiveStageTemplateValidationIssue.OutOfBounds(this)
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

object AdaptiveStageTemplateCatalogDefaults {
    // Value preserved verbatim (not renamed) -- this is a persisted default (see
    // CardsSettings.adaptiveStageTemplateId / LauncherSettingsJsonCodec's "timeScapeTemplateId" key),
    // and existing users' stored preference must still match it after the AdaptiveStage rename.
    val sharedCanvasId = AdaptiveStageTemplateId("timescape-shared-canvas")

    val sharedCanvas: AdaptiveStageTemplate =
        AdaptiveStageTemplate(
            id = sharedCanvasId,
            displayName = "Shared canvas",
            description = "A persistent canvas around app-stage cards.",
            variants =
                listOf(
                    variant(HomeLayoutDeviceClass.PHONE, AdaptiveStagePaneMode.COMPACT, 4, 7),
                    variant(HomeLayoutDeviceClass.PHONE, AdaptiveStagePaneMode.THREE_PANE, 5, 7),
                    variant(HomeLayoutDeviceClass.FOLDABLE, AdaptiveStagePaneMode.COMPACT, 8, 7),
                    variant(HomeLayoutDeviceClass.FOLDABLE, AdaptiveStagePaneMode.THREE_PANE, 10, 7),
                ),
        )

    val templates: List<AdaptiveStageTemplate> = listOf(sharedCanvas)

    private fun variant(
        deviceClass: HomeLayoutDeviceClass,
        paneMode: AdaptiveStagePaneMode,
        columns: Int,
        rows: Int,
    ): AdaptiveStageTemplateVariant =
        AdaptiveStageTemplateVariant(
            deviceClass = deviceClass,
            paneMode = paneMode,
            canvas =
                AdaptiveStageCanvas(
                    grid = GridDimensions(columns, rows),
                    elements =
                        listOf(
                            staticElement("clock", AdaptiveStageStaticElementType.CLOCK, 0, 0, columns, 1),
                            staticElement("search", AdaptiveStageStaticElementType.SEARCH, 0, 1, columns, 1),
                            staticElement("carousel", AdaptiveStageStaticElementType.APP_CAROUSEL, 0, 2, columns, 1),
                            staticElement("dock", AdaptiveStageStaticElementType.DOCK, 0, rows - 1, columns, 1),
                        ),
                ),
            dynamicSlots =
                listOf(
                    AdaptiveStageDynamicSlot(
                        id = AdaptiveStageDynamicSlotId("app-stage"),
                        source = AdaptiveStageDynamicSource.APP_STAGE_STACKS,
                        placement = GridPlacement(GridCell(0, 3), GridSpan(columns, rows - 4)),
                    ),
                    // Feed stages coexist with app stages in the same stage area rather than a
                    // separate canvas region (see ADR 0001, "Template binding and coexistence").
                    AdaptiveStageDynamicSlot(
                        id = AdaptiveStageDynamicSlotId("feed-stage"),
                        source = AdaptiveStageDynamicSource.FUTURE_FEED,
                        placement = GridPlacement(GridCell(0, 3), GridSpan(columns, rows - 4)),
                    ),
                ),
        )

    private fun staticElement(
        id: String,
        type: AdaptiveStageStaticElementType,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
    ): AdaptiveStageStaticElement =
        AdaptiveStageStaticElement(
            id = AdaptiveStageElementId(id),
            type = type,
            placement = GridPlacement(GridCell(column, row), GridSpan(columns, rows)),
        )
}

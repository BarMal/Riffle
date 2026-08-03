package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeScapeTemplateTest {
    @Test
    fun builtInTemplateKeepsCanvasElementsOutsideAppStageSlot() {
        val template = TimeScapeTemplateCatalogDefaults.sharedCanvas
        val variant = template.variantFor(HomeLayoutDeviceClass.PHONE, TimeScapePaneMode.COMPACT)

        assertTrue(variant != null)
        assertEquals(
            setOf(TimeScapeDynamicSource.APP_STAGE_STACKS, TimeScapeDynamicSource.FUTURE_FEED),
            variant.dynamicSlots.map { it.source }.toSet(),
        )
        assertEquals(listOf("clock", "search", "carousel", "dock"), variant.canvas.elements.map { it.id.value })
        assertEquals(emptyList(), variant.validate())
    }

    @Test
    fun builtInTemplateBindsFeedDynamicSlotToTheSharedStageAreaWithoutCollision() {
        val template = TimeScapeTemplateCatalogDefaults.sharedCanvas

        template.variants.forEach { variant ->
            val appStageSlot = variant.dynamicSlots.single { it.source == TimeScapeDynamicSource.APP_STAGE_STACKS }
            val feedSlot = variant.dynamicSlots.single { it.source == TimeScapeDynamicSource.FUTURE_FEED }

            assertEquals(appStageSlot.placement, feedSlot.placement)
            assertEquals(emptyList(), variant.validate())
        }
    }

    @Test
    fun builtInTemplateUsesIntentionalPhoneAndFoldableStageManagerPlacements() {
        val phone =
            TimeScapeTemplateCatalogDefaults.sharedCanvas.variantFor(
                HomeLayoutDeviceClass.PHONE,
                TimeScapePaneMode.THREE_PANE,
            )
        val foldable =
            TimeScapeTemplateCatalogDefaults.sharedCanvas.variantFor(
                HomeLayoutDeviceClass.FOLDABLE,
                TimeScapePaneMode.THREE_PANE,
            )

        assertTrue(phone != null)
        assertTrue(foldable != null)
        assertEquals(5, phone.canvas.grid.columns)
        assertEquals(10, foldable.canvas.grid.columns)
        assertEquals(emptyList(), phone.validate())
        assertEquals(emptyList(), foldable.validate())
    }

    @Test
    fun exactResponsiveVariantWinsAndDeviceFallsBackToCompact() {
        val compact = variant(TimeScapePaneMode.COMPACT)
        val stageManager = variant(TimeScapePaneMode.THREE_PANE)
        val template =
            TimeScapeTemplate(
                TimeScapeTemplateId("test"),
                "Test",
                "",
                listOf(compact, stageManager),
            )

        assertEquals(stageManager, template.variantFor(HomeLayoutDeviceClass.PHONE, TimeScapePaneMode.THREE_PANE))
        assertEquals(compact, template.variantFor(HomeLayoutDeviceClass.PHONE, TimeScapePaneMode.TWO_PANE))
        assertEquals(null, template.variantFor(HomeLayoutDeviceClass.FOLDABLE, TimeScapePaneMode.COMPACT))
    }

    @Test
    fun responsiveVariantCarriesConfiguredRailSide() {
        val variant = variant(TimeScapePaneMode.THREE_PANE).copy(railSide = TimeScapeRailSide.TRAILING)

        assertEquals(TimeScapeRailSide.TRAILING, variant.railSide)
    }

    @Test
    fun onlyTopAndBottomRailSidesAreHorizontalEdges() {
        assertTrue(TimeScapeRailSide.TOP.isHorizontalEdge)
        assertTrue(TimeScapeRailSide.BOTTOM.isHorizontalEdge)
        assertTrue(!TimeScapeRailSide.LEADING.isHorizontalEdge)
        assertTrue(!TimeScapeRailSide.TRAILING.isHorizontalEdge)
    }

    @Test
    fun validationReportsCollisionsAndOutOfBoundsPlacements() {
        val variant =
            TimeScapeTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                TimeScapePaneMode.COMPACT,
                TimeScapeCanvas(
                    GridDimensions(2, 2),
                    listOf(
                        element("first", GridPlacement(GridCell(0, 0))),
                        element("second", GridPlacement(GridCell(0, 0))),
                    ),
                ),
                listOf(
                    TimeScapeDynamicSlot(
                        TimeScapeDynamicSlotId("stage"),
                        TimeScapeDynamicSource.APP_STAGE_STACKS,
                        GridPlacement(GridCell(2, 0)),
                    ),
                ),
            )

        assertTrue(variant.validate().any { it is TimeScapeTemplateValidationIssue.Collision })
        assertTrue(variant.validate().any { it is TimeScapeTemplateValidationIssue.OutOfBounds })
    }

    @Test
    fun validationAllowsOverlappingDynamicSlotsButNotDynamicSlotOverlappingStaticElement() {
        val sharedPlacement = GridPlacement(GridCell(0, 0))
        val twoDynamicSlots =
            TimeScapeTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                TimeScapePaneMode.COMPACT,
                TimeScapeCanvas(GridDimensions(2, 2), emptyList()),
                listOf(
                    TimeScapeDynamicSlot(
                        TimeScapeDynamicSlotId("a"),
                        TimeScapeDynamicSource.APP_STAGE_STACKS,
                        sharedPlacement,
                    ),
                    TimeScapeDynamicSlot(
                        TimeScapeDynamicSlotId("b"),
                        TimeScapeDynamicSource.FUTURE_FEED,
                        sharedPlacement,
                    ),
                ),
            )
        val dynamicOverlappingStatic =
            TimeScapeTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                TimeScapePaneMode.COMPACT,
                TimeScapeCanvas(GridDimensions(2, 2), listOf(element("clock", sharedPlacement))),
                listOf(
                    TimeScapeDynamicSlot(
                        TimeScapeDynamicSlotId("a"),
                        TimeScapeDynamicSource.APP_STAGE_STACKS,
                        sharedPlacement,
                    ),
                ),
            )

        assertEquals(emptyList(), twoDynamicSlots.validate())
        assertTrue(dynamicOverlappingStatic.validate().any { it is TimeScapeTemplateValidationIssue.Collision })
    }

    @Test
    fun validationRejectsNonPositiveSpansBeforeEnumeratingCells() {
        val variant =
            variantWithPlacements(
                GridPlacement(GridCell(0, 0), GridSpan(columns = 0, rows = 1)),
                GridPlacement(GridCell(0, 0), GridSpan(columns = 1, rows = -1)),
            )

        val issues = variant.validate()

        assertEquals(2, issues.count { issue -> issue is TimeScapeTemplateValidationIssue.InvalidSpan })
    }

    @Test
    fun validationRejectsExtremeSpansWithoutEnumeratingCells() {
        val placement = GridPlacement(GridCell(0, 0), GridSpan(Int.MAX_VALUE, Int.MAX_VALUE))
        val variant = variantWithPlacements(placement)

        assertEquals(
            listOf(TimeScapeTemplateValidationIssue.OutOfBounds(placement)),
            variant.validate(),
        )
    }

    @Test
    fun validationHandlesExtremeInBoundsGridWithoutEnumeratingCells() {
        val fullGrid = GridPlacement(GridCell(0, 0), GridSpan(Int.MAX_VALUE, Int.MAX_VALUE))
        val overlappingCell = GridPlacement(GridCell(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1))
        val variant =
            TimeScapeTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                TimeScapePaneMode.COMPACT,
                TimeScapeCanvas(
                    GridDimensions(Int.MAX_VALUE, Int.MAX_VALUE),
                    listOf(
                        element("full-grid", fullGrid),
                        element("overlap", overlappingCell),
                    ),
                ),
                emptyList(),
            )

        assertEquals(
            listOf(TimeScapeTemplateValidationIssue.Collision(overlappingCell)),
            variant.validate(),
        )
    }

    private fun variant(mode: TimeScapePaneMode): TimeScapeTemplateVariant =
        TimeScapeTemplateVariant(
            HomeLayoutDeviceClass.PHONE,
            mode,
            TimeScapeCanvas(GridDimensions(4, 4), emptyList()),
            emptyList(),
        )

    private fun element(
        id: String,
        placement: GridPlacement,
    ): TimeScapeStaticElement =
        TimeScapeStaticElement(
            TimeScapeElementId(id),
            TimeScapeStaticElementType.CLOCK,
            placement,
        )

    private fun variantWithPlacements(vararg placements: GridPlacement): TimeScapeTemplateVariant =
        TimeScapeTemplateVariant(
            HomeLayoutDeviceClass.PHONE,
            TimeScapePaneMode.COMPACT,
            TimeScapeCanvas(
                GridDimensions(2, 2),
                placements.mapIndexed { index, placement -> element("element-$index", placement) },
            ),
            emptyList(),
        )
}

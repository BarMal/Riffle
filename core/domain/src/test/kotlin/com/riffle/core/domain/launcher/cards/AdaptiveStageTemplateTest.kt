package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveStageTemplateTest {
    @Test
    fun builtInTemplateKeepsCanvasElementsOutsideAppStageSlot() {
        val template = AdaptiveStageTemplateCatalogDefaults.sharedCanvas
        val variant = template.variantFor(HomeLayoutDeviceClass.PHONE, AdaptiveStagePaneMode.COMPACT)

        assertTrue(variant != null)
        assertEquals(
            setOf(AdaptiveStageDynamicSource.APP_STAGE_STACKS, AdaptiveStageDynamicSource.FUTURE_FEED),
            variant.dynamicSlots.map { it.source }.toSet(),
        )
        assertEquals(listOf("clock", "search", "carousel", "dock"), variant.canvas.elements.map { it.id.value })
        assertEquals(emptyList(), variant.validate())
    }

    @Test
    fun builtInTemplateBindsFeedDynamicSlotToTheSharedStageAreaWithoutCollision() {
        val template = AdaptiveStageTemplateCatalogDefaults.sharedCanvas

        template.variants.forEach { variant ->
            val appStageSlot = variant.dynamicSlots.single { it.source == AdaptiveStageDynamicSource.APP_STAGE_STACKS }
            val feedSlot = variant.dynamicSlots.single { it.source == AdaptiveStageDynamicSource.FUTURE_FEED }

            assertEquals(appStageSlot.placement, feedSlot.placement)
            assertEquals(emptyList(), variant.validate())
        }
    }

    @Test
    fun builtInTemplateUsesIntentionalPhoneAndFoldableStageManagerPlacements() {
        val phone =
            AdaptiveStageTemplateCatalogDefaults.sharedCanvas.variantFor(
                HomeLayoutDeviceClass.PHONE,
                AdaptiveStagePaneMode.THREE_PANE,
            )
        val foldable =
            AdaptiveStageTemplateCatalogDefaults.sharedCanvas.variantFor(
                HomeLayoutDeviceClass.FOLDABLE,
                AdaptiveStagePaneMode.THREE_PANE,
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
        val compact = variant(AdaptiveStagePaneMode.COMPACT)
        val stageManager = variant(AdaptiveStagePaneMode.THREE_PANE)
        val template =
            AdaptiveStageTemplate(
                AdaptiveStageTemplateId("test"),
                "Test",
                "",
                listOf(compact, stageManager),
            )

        assertEquals(stageManager, template.variantFor(HomeLayoutDeviceClass.PHONE, AdaptiveStagePaneMode.THREE_PANE))
        assertEquals(compact, template.variantFor(HomeLayoutDeviceClass.PHONE, AdaptiveStagePaneMode.TWO_PANE))
        assertEquals(null, template.variantFor(HomeLayoutDeviceClass.FOLDABLE, AdaptiveStagePaneMode.COMPACT))
    }

    @Test
    fun responsiveVariantCarriesConfiguredRailSide() {
        val variant = variant(AdaptiveStagePaneMode.THREE_PANE).copy(dockPosition = DockPosition.TRAILING)

        assertEquals(DockPosition.TRAILING, variant.dockPosition)
    }

    @Test
    fun onlyTopAndBottomRailSidesAreHorizontalEdges() {
        assertTrue(DockPosition.TOP.isHorizontalEdge)
        assertTrue(DockPosition.BOTTOM.isHorizontalEdge)
        assertTrue(!DockPosition.LEADING.isHorizontalEdge)
        assertTrue(!DockPosition.TRAILING.isHorizontalEdge)
    }

    @Test
    fun validationReportsCollisionsAndOutOfBoundsPlacements() {
        val variant =
            AdaptiveStageTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                AdaptiveStagePaneMode.COMPACT,
                AdaptiveStageCanvas(
                    GridDimensions(2, 2),
                    listOf(
                        element("first", GridPlacement(GridCell(0, 0))),
                        element("second", GridPlacement(GridCell(0, 0))),
                    ),
                ),
                listOf(
                    AdaptiveStageDynamicSlot(
                        AdaptiveStageDynamicSlotId("stage"),
                        AdaptiveStageDynamicSource.APP_STAGE_STACKS,
                        GridPlacement(GridCell(2, 0)),
                    ),
                ),
            )

        assertTrue(variant.validate().any { it is AdaptiveStageTemplateValidationIssue.Collision })
        assertTrue(variant.validate().any { it is AdaptiveStageTemplateValidationIssue.OutOfBounds })
    }

    @Test
    fun validationAllowsOverlappingDynamicSlotsButNotDynamicSlotOverlappingStaticElement() {
        val sharedPlacement = GridPlacement(GridCell(0, 0))
        val twoDynamicSlots =
            AdaptiveStageTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                AdaptiveStagePaneMode.COMPACT,
                AdaptiveStageCanvas(GridDimensions(2, 2), emptyList()),
                listOf(
                    AdaptiveStageDynamicSlot(
                        AdaptiveStageDynamicSlotId("a"),
                        AdaptiveStageDynamicSource.APP_STAGE_STACKS,
                        sharedPlacement,
                    ),
                    AdaptiveStageDynamicSlot(
                        AdaptiveStageDynamicSlotId("b"),
                        AdaptiveStageDynamicSource.FUTURE_FEED,
                        sharedPlacement,
                    ),
                ),
            )
        val dynamicOverlappingStatic =
            AdaptiveStageTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                AdaptiveStagePaneMode.COMPACT,
                AdaptiveStageCanvas(GridDimensions(2, 2), listOf(element("clock", sharedPlacement))),
                listOf(
                    AdaptiveStageDynamicSlot(
                        AdaptiveStageDynamicSlotId("a"),
                        AdaptiveStageDynamicSource.APP_STAGE_STACKS,
                        sharedPlacement,
                    ),
                ),
            )

        assertEquals(emptyList(), twoDynamicSlots.validate())
        assertTrue(dynamicOverlappingStatic.validate().any { it is AdaptiveStageTemplateValidationIssue.Collision })
    }

    @Test
    fun validationRejectsNonPositiveSpansBeforeEnumeratingCells() {
        val variant =
            variantWithPlacements(
                GridPlacement(GridCell(0, 0), GridSpan(columns = 0, rows = 1)),
                GridPlacement(GridCell(0, 0), GridSpan(columns = 1, rows = -1)),
            )

        val issues = variant.validate()

        assertEquals(2, issues.count { issue -> issue is AdaptiveStageTemplateValidationIssue.InvalidSpan })
    }

    @Test
    fun validationRejectsExtremeSpansWithoutEnumeratingCells() {
        val placement = GridPlacement(GridCell(0, 0), GridSpan(Int.MAX_VALUE, Int.MAX_VALUE))
        val variant = variantWithPlacements(placement)

        assertEquals(
            listOf(AdaptiveStageTemplateValidationIssue.OutOfBounds(placement)),
            variant.validate(),
        )
    }

    @Test
    fun validationHandlesExtremeInBoundsGridWithoutEnumeratingCells() {
        val fullGrid = GridPlacement(GridCell(0, 0), GridSpan(Int.MAX_VALUE, Int.MAX_VALUE))
        val overlappingCell = GridPlacement(GridCell(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1))
        val variant =
            AdaptiveStageTemplateVariant(
                HomeLayoutDeviceClass.PHONE,
                AdaptiveStagePaneMode.COMPACT,
                AdaptiveStageCanvas(
                    GridDimensions(Int.MAX_VALUE, Int.MAX_VALUE),
                    listOf(
                        element("full-grid", fullGrid),
                        element("overlap", overlappingCell),
                    ),
                ),
                emptyList(),
            )

        assertEquals(
            listOf(AdaptiveStageTemplateValidationIssue.Collision(overlappingCell)),
            variant.validate(),
        )
    }

    private fun variant(mode: AdaptiveStagePaneMode): AdaptiveStageTemplateVariant =
        AdaptiveStageTemplateVariant(
            HomeLayoutDeviceClass.PHONE,
            mode,
            AdaptiveStageCanvas(GridDimensions(4, 4), emptyList()),
            emptyList(),
        )

    private fun element(
        id: String,
        placement: GridPlacement,
    ): AdaptiveStageStaticElement =
        AdaptiveStageStaticElement(
            AdaptiveStageElementId(id),
            AdaptiveStageStaticElementType.CLOCK,
            placement,
        )

    private fun variantWithPlacements(vararg placements: GridPlacement): AdaptiveStageTemplateVariant =
        AdaptiveStageTemplateVariant(
            HomeLayoutDeviceClass.PHONE,
            AdaptiveStagePaneMode.COMPACT,
            AdaptiveStageCanvas(
                GridDimensions(2, 2),
                placements.mapIndexed { index, placement -> element("element-$index", placement) },
            ),
            emptyList(),
        )
}

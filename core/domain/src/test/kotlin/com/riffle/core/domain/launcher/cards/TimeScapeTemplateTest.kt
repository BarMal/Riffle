package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
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
        assertEquals(TimeScapeDynamicSource.APP_STAGE_STACKS, variant.dynamicSlots.single().source)
        assertEquals(listOf("clock", "carousel", "dock"), variant.canvas.elements.map { it.id.value })
        assertEquals(emptyList(), variant.validate())
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
}

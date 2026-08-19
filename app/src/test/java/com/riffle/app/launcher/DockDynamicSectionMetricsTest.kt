package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the dock's run is divided between its two sections.
 *
 * The static side is sized first and in full; the dynamic one takes what is left. These pin that
 * ordering, and the arithmetic that turns the two into one run.
 */
class DockDynamicSectionMetricsTest {
    @Test
    fun dynamicSectionTakesWhatTheEntriesNeedWhenTheRunHasRoom() {
        // Four entries at a 44dp icon and 12dp spacing: 4*44 + 3*12.
        assertEquals(
            212,
            dockDynamicSectionMainAxisDp(
                entryCount = 4,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 240,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionTakesOnlyWhatTheStaticSideLeaves() {
        // 560 of run, 400 already spoken for by the static side, 17 for the rule between them.
        assertEquals(
            143,
            dockDynamicSectionMainAxisDp(
                entryCount = 12,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 400,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun aStaticSideThatFillsTheRunLeavesNoDynamicSection() {
        // Not a negative width: a dock the user has filled keeps every slot it has, and the
        // section it leaves no room for is simply not drawn.
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 3,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 560,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun noEntriesMeansNoSection() {
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 0,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 100,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun aDockWithNoDynamicSectionRunsExactlyAsLongAsItsStaticSide() {
        // The invariant every caller that predates the section relies on.
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
            )

        assertEquals(240, metrics.surfaceMainAxisDp)
    }

    @Test
    fun aDockWithADynamicSectionRunsLongEnoughForBothAndTheRuleBetween() {
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 100,
            )

        assertEquals(240 + 17 + 100, metrics.surfaceMainAxisDp)
    }
}

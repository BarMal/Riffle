package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the dock's run is divided between its two sections.
 *
 * The static side is sized from its own [com.riffle.core.domain.launcher.home.DockModel.capacity],
 * never shrunk to make room for the dynamic section; the dynamic section is sized from its own
 * [com.riffle.core.domain.launcher.home.DockModel.notificationSlotCount] cap, never padded out
 * beyond how many entries actually exist. The two are independent budgets that only add together
 * into one run -- these pin that arithmetic.
 */
class DockDynamicSectionMetricsTest {
    @Test
    fun dynamicSectionTakesWhatTheEntriesNeedWhenTheRunHasRoom() {
        // Four entries at a 44dp icon and 12dp spacing: 4*44 + 3*12.
        assertEquals(
            212,
            dockDynamicSectionMainAxisDp(
                entryCount = 4,
                notificationSlotCount = 5,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 240,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionShrinksToFewerEntriesThanTheSlotCountAllows() {
        // Only two entries even though the slot count allows five: 2*44 + 1*12.
        assertEquals(
            100,
            dockDynamicSectionMainAxisDp(
                entryCount = 2,
                notificationSlotCount = 5,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 240,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionNeverExceedsItsOwnSlotCountRegardlessOfHowManyEntriesThereAre() {
        // Five entries but a slot count of two: 2*44 + 1*12, the rest scrolls (DockDynamicSection).
        assertEquals(
            100,
            dockDynamicSectionMainAxisDp(
                entryCount = 5,
                notificationSlotCount = 2,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 240,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionTakesOnlyWhatTheRunHasRoomForOnATightScreen() {
        // 560 of run, 400 already spoken for by the static side, 17 for the rule between them --
        // the two budgets are independent, but the whole strip still can't draw past the screen.
        assertEquals(
            143,
            dockDynamicSectionMainAxisDp(
                entryCount = 12,
                notificationSlotCount = 12,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 400,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun aStaticSideThatFillsTheRunLeavesNoDynamicSection() {
        // Not a negative width: a static side sized to the full run on a tight screen leaves the
        // section it has no room for simply undrawn, rather than negative or crashing.
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 3,
                notificationSlotCount = 3,
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
                notificationSlotCount = 3,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = 100,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun aZeroSlotCountMeansNoSectionEvenWithEntries() {
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 3,
                notificationSlotCount = 0,
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
    fun stripWithNoEntriesDoesNotDrawTheDynamicSection() {
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
                dynamicSectionMainAxisDp = 0,
            )

        assertEquals(false, dockSurfaceStripShowsDynamicSection(metrics, dynamicEntries = emptyList()))
        assertEquals(240, dockSurfaceStripMainAxisDp(metrics, showDynamicSection = false))
    }

    @Test
    fun stripWithEntriesDrawsTheDynamicSectionAtTheFullSurfaceWidth() {
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
                dynamicSectionMainAxisDp = 61,
            )
        val entries =
            listOf(
                DockDynamicEntry(
                    key = "app",
                    label = "App",
                    identity = null,
                    badgeCount = 1,
                    isSelected = false,
                    contentDescription = "App, 1 notification",
                    intent = null,
                ),
            )

        assertEquals(true, dockSurfaceStripShowsDynamicSection(metrics, dynamicEntries = entries))
        assertEquals(
            metrics.surfaceMainAxisDp,
            dockSurfaceStripMainAxisDp(metrics, showDynamicSection = true),
        )
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

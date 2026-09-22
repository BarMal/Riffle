package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the dock's run is divided between its two sections.
 *
 * The static side is sized first, but capped short of the full run once there is at least one
 * dynamic entry to show, so that entry always has a tile's worth of room reserved for it; the
 * dynamic side then takes whatever the static side actually used plus that reservation. These pin
 * that ordering, and the arithmetic that turns the two into one run.
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
    fun reservationHoldsBackOneEntryAndTheDividerWhenThereAreEntries() {
        assertEquals(
            44 + 17,
            dockDynamicSectionReservedMainAxisDp(entryCount = 3, entryExtentDp = 44),
        )
    }

    @Test
    fun reservationIsZeroWithNoEntries() {
        assertEquals(0, dockDynamicSectionReservedMainAxisDp(entryCount = 0, entryExtentDp = 44))
    }

    @Test
    fun aFullDockStillLeavesRoomForOneDynamicEntryOnceTheStaticSideReservesForIt() {
        // Without a reservation, a static side sized to fill the whole 560dp run would leave the
        // dynamic section nothing -- this is the "compressed to uselessness when folded" bug: the
        // static side must be capped short of the full run first so the entry it reserved for is
        // actually still there once the dynamic section asks for its share.
        val reserved = dockDynamicSectionReservedMainAxisDp(entryCount = 3, entryExtentDp = 44)
        val staticContainerMainAxisDp =
            dockContainerMainAxisDp(
                availableMainAxisDp = 560,
                slotCount = 20,
                iconSizeDp = 44,
                itemSpacingDp = 12,
                backgroundSizing = DockBackgroundSizing.FIXED,
                reservedDynamicSectionMainAxisDp = reserved,
            )

        val dynamicSection =
            dockDynamicSectionMainAxisDp(
                entryCount = 3,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                staticContainerMainAxisDp = staticContainerMainAxisDp,
                maxRunMainAxisDp = 560,
            )

        assertEquals(true, dynamicSection >= 44)
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
    fun stripWithReservedRoomButNoEntriesDoesNotDrawTheDynamicSection() {
        // A reservation with nothing to show it -- ExpandedDockSurface's case, since the shelf's
        // own card row carries the entries instead. The strip must not draw an empty divider for it.
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

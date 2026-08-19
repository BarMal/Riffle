package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class DockOverflowModePolicyTest {
    @Test
    fun classifiesFiveIconsOnFoldedNarrowWidthAsFitByCompaction() {
        assertEquals(
            DockOverflowMode.FitByCompaction,
            dockOverflowMode(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableMainAxisDp = 252,
            ),
        )
    }

    @Test
    fun classifiesWiderFiveIconLayoutAsFits() {
        assertEquals(
            DockOverflowMode.Fits,
            dockOverflowMode(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableMainAxisDp = 280,
            ),
        )
    }

    @Test
    fun classifiesWideExpandedSpacingLayoutAsFits() {
        assertEquals(
            DockOverflowMode.Fits,
            dockOverflowMode(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableMainAxisDp = 376,
            ),
        )
    }

    @Test
    fun classifiesHardMinimumBoundaryAsFitByCompaction() {
        assertEquals(
            DockOverflowMode.FitByCompaction,
            dockOverflowMode(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableMainAxisDp = 160,
            ),
        )
    }

    @Test
    fun theAxisTheDockRunsAlongIsWhatDecidesWhetherItsSlotsFit() {
        // Eight slots need 454dp of run. The same dock on the same phone therefore has to be
        // classified differently depending on which way it is laid out -- which is the whole reason
        // this takes a main-axis extent and not a width.
        val slots = 8
        val iconSizeDp = 48
        val itemSpacingDp = 10

        assertEquals(
            DockOverflowMode.FitByCompaction,
            dockOverflowMode(
                slotCount = slots,
                iconSizeDp = iconSizeDp,
                itemSpacingDp = itemSpacingDp,
                availableMainAxisDp = 360,
            ),
        )
        assertEquals(
            DockOverflowMode.Fits,
            dockOverflowMode(
                slotCount = slots,
                iconSizeDp = iconSizeDp,
                itemSpacingDp = itemSpacingDp,
                availableMainAxisDp = 800,
            ),
        )
    }
}

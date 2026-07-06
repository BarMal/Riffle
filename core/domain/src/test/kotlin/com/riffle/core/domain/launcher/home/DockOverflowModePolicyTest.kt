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
                minIconSizeDp = 32,
                availableWidthDp = 252,
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
                minIconSizeDp = 32,
                availableWidthDp = 280,
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
                minIconSizeDp = 32,
                availableWidthDp = 376,
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
                minIconSizeDp = 32,
                availableWidthDp = 160,
            ),
        )
    }

    @Test
    fun classifiesTooNarrowHardMinimumCaseAsRequiresOverflowNavigation() {
        assertEquals(
            DockOverflowMode.RequiresOverflowNavigation,
            dockOverflowMode(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                minIconSizeDp = 32,
                availableWidthDp = 159,
            ),
        )
    }
}

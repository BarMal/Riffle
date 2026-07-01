package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockMetricsTest {
    @Test
    fun defaultIconSizeKeepsExistingDockHeight() {
        assertEquals(76, dockHeightDp(iconSizeDp = 44))
    }

    @Test
    fun largerIconSizeIncreasesDockHeight() {
        assertEquals(96, dockHeightDp(iconSizeDp = 64))
    }

    @Test
    fun normalDockRendersOccupiedSlotsSoIconsFillTheDock() {
        assertEquals(
            4,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun normalDockDoesNotRenderMoreSlotsThanCapacity() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 6,
                isEditing = false,
            ),
        )
    }

    @Test
    fun editingDockRendersCapacitySlots() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 4,
                isEditing = true,
            ),
        )
    }

    @Test
    fun editingDockRendersConfiguredSlotsAboveSix() {
        assertEquals(
            8,
            dockRenderedSlotCount(
                capacity = 8,
                itemCount = 8,
                isEditing = true,
            ),
        )
    }

    @Test
    fun emptyDynamicDockRendersNoSlots() {
        assertEquals(
            0,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
            ),
        )
    }

    @Test
    fun fixedDockOnlyRendersOccupiedSlotsWhenNotEditing() {
        assertEquals(
            2,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 2,
                isEditing = false,
            ),
        )
    }

    @Test
    fun zeroCapacityDockRendersNoSlots() {
        assertEquals(
            0,
            dockRenderedSlotCount(
                capacity = 0,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun emptyDynamicDockHidesBackground() {
        assertEquals(
            false,
            dockBackgroundVisible(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
            ),
        )
    }

    @Test
    fun emptyFixedDockShowsBackground() {
        assertEquals(
            true,
            dockBackgroundVisible(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }

    @Test
    fun zeroCapacityFixedDockHidesBackground() {
        assertEquals(
            false,
            dockBackgroundVisible(
                capacity = 0,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }
}

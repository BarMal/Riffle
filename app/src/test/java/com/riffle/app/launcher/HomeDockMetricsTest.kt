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
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
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
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
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
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
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
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
            ),
        )
    }

    @Test
    fun fixedDockRendersCapacitySlots() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 2,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.FIXED,
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
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }
}

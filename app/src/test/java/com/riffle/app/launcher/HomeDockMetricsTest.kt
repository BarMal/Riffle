package com.riffle.app.launcher

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
        assertEquals(4, dockRenderedSlotCount(capacity = 5, itemCount = 4, isEditing = false))
    }

    @Test
    fun normalDockDoesNotRenderMoreSlotsThanCapacity() {
        assertEquals(5, dockRenderedSlotCount(capacity = 5, itemCount = 6, isEditing = false))
    }

    @Test
    fun editingDockRendersCapacitySlots() {
        assertEquals(5, dockRenderedSlotCount(capacity = 5, itemCount = 4, isEditing = true))
    }

    @Test
    fun emptyNormalDockKeepsCapacitySlotsForFutureSlotSurfaces() {
        assertEquals(5, dockRenderedSlotCount(capacity = 5, itemCount = 0, isEditing = false))
    }

    @Test
    fun zeroCapacityDockRendersNoSlots() {
        assertEquals(0, dockRenderedSlotCount(capacity = 0, itemCount = 4, isEditing = false))
    }
}

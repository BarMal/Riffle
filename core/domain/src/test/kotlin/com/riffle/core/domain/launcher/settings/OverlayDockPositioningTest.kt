package com.riffle.core.domain.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockPositioningTest {
    @Test
    fun mapsDragPixelsToDensityAwareVerticalOffset() {
        assertEquals(
            24,
            overlayDockVerticalOffsetFromDrag(
                startOffsetDp = 0,
                dragDeltaPx = 72f,
                density = 3f,
            ),
        )
    }

    @Test
    fun clampsDraggedVerticalOffset() {
        assertEquals(
            MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
            overlayDockVerticalOffsetFromDrag(
                startOffsetDp = 220,
                dragDeltaPx = 200f,
                density = 2f,
            ),
        )
        assertEquals(
            MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
            overlayDockVerticalOffsetFromDrag(
                startOffsetDp = -220,
                dragDeltaPx = -200f,
                density = 2f,
            ),
        )
    }

    @Test
    fun keepsOffsetClampedWhenDensityIsInvalid() {
        assertEquals(
            MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
            overlayDockVerticalOffsetFromDrag(
                startOffsetDp = 999,
                dragDeltaPx = 100f,
                density = 0f,
            ),
        )
    }
}

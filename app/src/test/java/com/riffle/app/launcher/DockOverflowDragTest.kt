package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockOverflowDragTest {
    @Test
    fun edgeAutoScrollUsesBoundedDeltasOutsideTheViewportEdges() {
        assertEquals(
            -24f,
            dockEdgeAutoScrollDelta(
                pointerX = -12f,
                viewportWidthPx = 200f,
                edgeZonePx = 24f,
            ),
            0.001f,
        )
        assertEquals(
            24f,
            dockEdgeAutoScrollDelta(
                pointerX = 224f,
                viewportWidthPx = 200f,
                edgeZonePx = 24f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            dockEdgeAutoScrollDelta(
                pointerX = 100f,
                viewportWidthPx = 200f,
                edgeZonePx = 24f,
            ),
            0.001f,
        )
    }
}

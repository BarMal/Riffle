package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockOverflowDragTest {
    @Test
    fun dragCandidateKeepsItsSlotWhilePointerJittersAtTheBoundary() {
        var candidate = 0

        candidate = target(candidate, draggedSlotDeltaPx = 35f)
        candidate = target(candidate, draggedSlotDeltaPx = 28f)
        candidate = target(candidate, draggedSlotDeltaPx = 35f)

        assertEquals(0, candidate)

        candidate = target(candidate, draggedSlotDeltaPx = 38f)
        candidate = target(candidate, draggedSlotDeltaPx = 21f)

        assertEquals(1, candidate)

        candidate = target(candidate, draggedSlotDeltaPx = 19f)

        assertEquals(0, candidate)
    }

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

    private fun target(
        currentTargetIndex: Int,
        draggedSlotDeltaPx: Float,
    ): Int =
        dockDragTargetIndex(
            originIndex = 0,
            currentTargetIndex = currentTargetIndex,
            draggedSlotDeltaPx = draggedSlotDeltaPx,
            slotWidthPx = 56f,
            itemCount = 4,
        )
}

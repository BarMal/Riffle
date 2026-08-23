package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun pullingAnItemOffTheDockMovesItHomeWhicheverEdgeTheDockIsOn() {
        // The threshold is one comparison because the drag arrives already measured off the edge,
        // so a side dock's sideways pull and a bottom dock's upward one are the same number here.
        listOf(DockPosition.BOTTOM, DockPosition.TOP, DockPosition.LEFT, DockPosition.RIGHT)
            .forEach { position ->
                val awayFromEdgePx = position.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = 0f) + 60f
                assertEquals(
                    LauncherShellAction.MoveDockItemToHome(ITEM_ID),
                    dockDragDropAction(
                        itemId = ITEM_ID,
                        originIndex = 0,
                        targetIndex = 0,
                        awayFromEdgePx = awayFromEdgePx,
                        dockItemSizePx = 48f,
                    ),
                )
            }
    }

    @Test
    fun aPullShorterThanOneItemReordersInsteadOfLeaving() {
        assertEquals(
            LauncherShellAction.MoveDockShortcutToIndex(ITEM_ID, 2),
            dockDragDropAction(
                itemId = ITEM_ID,
                originIndex = 0,
                targetIndex = 2,
                awayFromEdgePx = 47f,
                dockItemSizePx = 48f,
            ),
        )
    }

    @Test
    fun aDragThatEndsWhereItStartedMeansNothing() {
        assertNull(
            dockDragDropAction(
                itemId = ITEM_ID,
                originIndex = 1,
                targetIndex = 1,
                awayFromEdgePx = 0f,
                dockItemSizePx = 48f,
            ),
        )
    }

    @Test
    fun pushingIntoTheDocksOwnEdgeNeverLeavesIt() {
        // Dragging a bottom dock's item downward is not a way out of the dock.
        assertNull(
            dockDragDropAction(
                itemId = ITEM_ID,
                originIndex = 1,
                targetIndex = 1,
                awayFromEdgePx = DockPosition.BOTTOM.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = 400f),
                dockItemSizePx = 48f,
            ),
        )
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

    private companion object {
        private val ITEM_ID = LauncherItemId("app:camera")
    }
}

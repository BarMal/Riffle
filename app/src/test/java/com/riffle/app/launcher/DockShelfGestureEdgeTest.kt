package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shelf comes out of the edge the dock is on, so the gesture that opens it is a pull away from
 * that edge -- upward from a bottom dock, sideways from a side one.
 */
class DockShelfGestureEdgeTest {
    @Test
    fun aSideDockOpensOnAPullAwayFromItsEdge() {
        assertEquals(
            true,
            expandedState(isExpanded = false, x = PAST_THRESHOLD, y = 10f, position = DockPosition.LEFT),
        )
        assertEquals(
            true,
            expandedState(isExpanded = false, x = -PAST_THRESHOLD, y = 10f, position = DockPosition.RIGHT),
        )
    }

    @Test
    fun aSideDockClosesOnAPushBackTowardItsEdge() {
        assertEquals(
            false,
            expandedState(isExpanded = true, x = -PAST_THRESHOLD, y = 10f, position = DockPosition.LEFT),
        )
        assertEquals(
            false,
            expandedState(isExpanded = true, x = PAST_THRESHOLD, y = 10f, position = DockPosition.RIGHT),
        )
    }

    @Test
    fun aSwipeUpMeansNothingToASideDock() {
        // It is along the dock's run, not away from its edge, so it is not the shelf's gesture.
        assertEquals(
            null,
            expandedState(isExpanded = false, x = 0f, y = -PAST_THRESHOLD, position = DockPosition.LEFT),
        )
    }

    @Test
    fun aLeftDockOpensWithARightwardPull() {
        // The left edge is physical, so the pull that opens it is always rightward -- pushing back
        // into the edge (leftward) is not the opening gesture.
        assertEquals(
            true,
            expandedState(isExpanded = false, x = PAST_THRESHOLD, y = 0f, position = DockPosition.LEFT),
        )
        assertEquals(
            null,
            expandedState(isExpanded = false, x = -PAST_THRESHOLD, y = 0f, position = DockPosition.LEFT),
        )
    }

    @Test
    fun aBottomDockKeepsTheSwipeItAlwaysHad() {
        assertEquals(
            true,
            expandedState(isExpanded = false, x = 10f, y = -PAST_THRESHOLD, position = DockPosition.BOTTOM),
        )
        assertEquals(
            false,
            expandedState(isExpanded = true, x = 10f, y = PAST_THRESHOLD, position = DockPosition.BOTTOM),
        )
        assertEquals(
            null,
            expandedState(isExpanded = false, x = PAST_THRESHOLD, y = 0f, position = DockPosition.BOTTOM),
        )
    }

    @Test
    fun theDragIsClaimedOnlyInTheDirectionTheShelfWouldMove() {
        assertTrue(
            dockShelfGestureClaimsDrag(
                isExpanded = false,
                horizontalDragPx = PAST_CLAIM_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEFT,
            ),
        )
        // Pushing into a closed side dock's own edge is not the start of opening it.
        assertFalse(
            dockShelfGestureClaimsDrag(
                isExpanded = false,
                horizontalDragPx = -PAST_CLAIM_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEFT,
            ),
        )
    }

    private fun expandedState(
        isExpanded: Boolean,
        x: Float,
        y: Float,
        position: DockPosition,
    ): Boolean? =
        dockShelfGestureExpandedState(
            isExpanded = isExpanded,
            horizontalDragPx = x,
            verticalDragPx = y,
            position = position,
        )

    private companion object {
        private const val PAST_THRESHOLD = 90f
        private const val PAST_CLAIM_THRESHOLD = 30f
    }
}

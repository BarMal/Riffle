package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.DockPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullDirectionTest {
    @Test
    fun eachEdgePullsTowardTheInterior() {
        assertEquals(DockPullDirection.UP, DockPosition.BOTTOM.pullDirection)
        assertEquals(DockPullDirection.DOWN, DockPosition.TOP.pullDirection)
        assertEquals(DockPullDirection.RIGHT, DockPosition.LEFT.pullDirection)
        assertEquals(DockPullDirection.LEFT, DockPosition.RIGHT.pullDirection)
    }

    @Test
    fun onlyTheComponentAlongThePullCounts() {
        // Screen coordinates: y grows downward, so pulling up is a negative dy.
        assertEquals(30f, DockPullDirection.UP.pullDistance(dx = 50f, dy = -30f))
        assertEquals(30f, DockPullDirection.DOWN.pullDistance(dx = -50f, dy = 30f))
        assertEquals(20f, DockPullDirection.RIGHT.pullDistance(dx = 20f, dy = 99f))
        assertEquals(20f, DockPullDirection.LEFT.pullDistance(dx = -20f, dy = -99f))
    }

    @Test
    fun aDragIntoTheEdgePullsNothing() {
        assertEquals(0f, DockPullDirection.UP.pullDistance(dx = 0f, dy = 40f))
        assertEquals(0f, DockPullDirection.DOWN.pullDistance(dx = 0f, dy = -40f))
        assertEquals(0f, DockPullDirection.RIGHT.pullDistance(dx = -40f, dy = 0f))
        assertEquals(0f, DockPullDirection.LEFT.pullDistance(dx = 40f, dy = 0f))
    }

    @Test
    fun aPurelyCrosswiseDragPullsNothing() {
        assertEquals(0f, DockPullDirection.UP.pullDistance(dx = 80f, dy = 0f))
        assertEquals(0f, DockPullDirection.RIGHT.pullDistance(dx = 0f, dy = -80f))
    }

    @Test
    fun alongIsSignedSoAPullCanBeTakenBack() {
        assertEquals(-40f, DockPullDirection.UP.along(dx = 10f, dy = 40f))
        assertEquals(40f, DockPullDirection.UP.along(dx = 10f, dy = -40f))
        assertEquals(-15f, DockPullDirection.LEFT.along(dx = 15f, dy = 3f))
    }
}

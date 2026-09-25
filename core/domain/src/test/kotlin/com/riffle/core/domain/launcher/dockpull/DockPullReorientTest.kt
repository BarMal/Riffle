package com.riffle.core.domain.launcher.dockpull

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockPullReorientTest {
    @Test
    fun frostStrengthIsZeroAtRestAndOneFullyPulled() {
        assertEquals(0f, dockPullReorientFrostStrength(dockBackgroundAlpha = 1f))
        assertEquals(1f, dockPullReorientFrostStrength(dockBackgroundAlpha = 0f))
        assertEquals(0.5f, dockPullReorientFrostStrength(dockBackgroundAlpha = 0.5f))
    }

    @Test
    fun frostStrengthIsCoercedForAnOutOfRangeAlpha() {
        assertEquals(1f, dockPullReorientFrostStrength(dockBackgroundAlpha = -1f))
        assertEquals(0f, dockPullReorientFrostStrength(dockBackgroundAlpha = 2f))
    }

    @Test
    fun tiltEasesBackToFlatAsTheFrostClears() {
        assertEquals(0f, dockPullReorientTiltDegrees(dockBackgroundAlpha = 1f, maxDegrees = 6f))
        assertEquals(6f, dockPullReorientTiltDegrees(dockBackgroundAlpha = 0f, maxDegrees = 6f))
        assertEquals(3f, dockPullReorientTiltDegrees(dockBackgroundAlpha = 0.5f, maxDegrees = 6f))
    }

    @Test
    fun staggerGrowsWithIndexUpToTheCap() {
        assertEquals(0, dockPullItemStaggerDelayMillis(index = 0, stepMillis = 20, capMillis = 120))
        assertEquals(40, dockPullItemStaggerDelayMillis(index = 2, stepMillis = 20, capMillis = 120))
        assertEquals(120, dockPullItemStaggerDelayMillis(index = 50, stepMillis = 20, capMillis = 120))
    }

    @Test
    fun staggerNeverGoesNegativeForANegativeIndex() {
        assertEquals(0, dockPullItemStaggerDelayMillis(index = -3))
    }

    @Test
    fun anItemWithinDestinationCapacityFits() {
        assertTrue(dockPullItemFitsDestination(index = 0, destinationCapacity = 3))
        assertTrue(dockPullItemFitsDestination(index = 2, destinationCapacity = 3))
    }

    @Test
    fun anItemBeyondDestinationCapacityDoesNotFit() {
        assertFalse(dockPullItemFitsDestination(index = 3, destinationCapacity = 3))
        assertFalse(dockPullItemFitsDestination(index = 0, destinationCapacity = 0))
    }
}

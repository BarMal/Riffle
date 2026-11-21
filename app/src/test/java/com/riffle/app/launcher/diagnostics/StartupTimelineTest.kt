package com.riffle.app.launcher.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartupTimelineTest {
    @Before
    @After
    fun reset() = StartupTimeline.clear()

    @Test
    fun aLabelKeepsItsFirstTimeAndOrderIsTheOrderReached() {
        assertTrue(StartupTimeline.mark("a", 10))
        assertTrue(StartupTimeline.mark("b", 20))
        assertFalse(StartupTimeline.mark("a", 99))

        assertEquals(listOf("a" to 10L, "b" to 20L), StartupTimeline.snapshot().map { it.label to it.millis })
        assertEquals(10L, StartupTimeline.millisOf("a"))
        assertNull(StartupTimeline.millisOf("never"))
    }

    @Test
    fun marksAreBoundedAndNegativeTimesAreHeldAtZero() {
        repeat(100) { StartupTimeline.mark("m$it", -5) }

        assertEquals(40, StartupTimeline.snapshot().size)
        assertEquals(0L, StartupTimeline.snapshot().first().millis)
    }

    @Test
    fun markingWithoutAPlatformClockNeverThrows() {
        // A plain JVM test has no platform clock: the stamp falls back to zero rather than failing a launch.
        StartupTimeline.mark("no clock")

        assertEquals(1, StartupTimeline.snapshot().size)
    }
}

package com.riffle.core.domain.launcher.dock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockEdgeFadeTest {
    private fun alpha(
        start: Float,
        end: Float,
    ) = DockEdgeFade.alpha(itemStart = start, itemEnd = end, viewportStart = 0f, viewportEnd = 300f)

    @Test
    fun aWholeIconInViewIsUntouchedWhereverItStands() {
        assertEquals(1f, alpha(0f, 48f), 0f)
        assertEquals(1f, alpha(100f, 148f), 0f)
        assertEquals(1f, alpha(252f, 300f), 0f)
    }

    @Test
    fun aSubPixelDifferenceBetweenTheEdgesIsNotACut() {
        assertEquals(1f, alpha(-0.4f, 47.6f), 0f)
        assertEquals(1f, alpha(252.4f, 300.4f), 0f)
    }

    @Test
    fun anIconHalfCutIsAQuarterAndOneFullyOutIsGone() {
        assertEquals(0.25f, alpha(276f, 324f), 1e-4f)
        assertEquals(0.25f, alpha(-24f, 24f), 1e-4f)
        assertEquals(0f, alpha(300f, 348f), 0f)
        assertEquals(0f, alpha(-60f, -12f), 0f)
    }

    @Test
    fun scrollingSlidesAnIconThroughTheValuesWithNoStep() {
        var previous = alpha(300f, 348f)
        for (x in 299 downTo 252) {
            val a = alpha(x.toFloat(), x + 48f)
            assertTrue("it only gets stronger as it comes into view: $x", a >= previous)
            assertTrue("and never jumps: $x", a - previous < 0.1f)
            previous = a
        }
        assertEquals(1f, previous, 0f)
    }

    @Test
    fun anEmptyExtentIsLeftAlone() {
        assertEquals(1f, DockEdgeFade.alpha(10f, 10f, 0f, 5f), 0f)
    }
}

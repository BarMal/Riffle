package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamGridFadeTest {
    private val column = StreamColumnRect(left = 440f, top = 400f, right = 760f, bottom = 900f)

    /** An item well inside the column, deeper than the fade goes on for. */
    private val deepX = 600f
    private val deepY = 650f

    @Test
    fun withNoColumnNothingFades() {
        assertEquals(1f, StreamGridFade.alpha(500f, 500f, null, peekThrough = true))
        assertEquals(1f, StreamGridFade.alpha(500f, 500f, StreamColumnRect(0f, 0f, 0f, 0f), peekThrough = false))
    }

    @Test
    fun wellUnderTheColumnAnItemKeepsOnlyWhatPeekThroughAllowsOrNothing() {
        assertEquals(
            StreamGridFade.PEEK_ALPHA,
            StreamGridFade.alpha(deepX, deepY, column, peekThrough = true),
            0.0001f,
        )
        assertEquals(0f, StreamGridFade.alpha(deepX, deepY, column, peekThrough = false))
    }

    @Test
    fun wellClearOfTheColumnAnItemIsUntouched() {
        assertEquals(1f, StreamGridFade.alpha(440f - StreamGridFade.FEATHER_DP, 600f, column, peekThrough = false))
        assertEquals(1f, StreamGridFade.alpha(100f, 100f, column, peekThrough = false))
    }

    @Test
    fun theFadeEasesWithoutAStepAcrossTheColumnsEdgeInAndOut() {
        var previous = StreamGridFade.alpha(440f - StreamGridFade.FEATHER_DP, 600f, column, peekThrough = true)
        var x = 440f - StreamGridFade.FEATHER_DP
        while (x < 440f + StreamGridFade.INNER_DP) {
            x += 4f
            val now = StreamGridFade.alpha(x, 600f, column, peekThrough = true)
            assertTrue(now <= previous, "never lighter further in: $now > $previous at $x")
            assertTrue(previous - now < 0.03f, "no hard cut: ${previous - now} at $x")
            previous = now
        }
        assertEquals(StreamGridFade.PEEK_ALPHA, previous, 0.0001f)
    }

    @Test
    fun appsOneColumnApartNeverStepFromFullStrengthToTheFloor() {
        // A grid column is about 135 dp wide: the centres of neighbouring columns are that far apart.
        val pitch = 135f
        var x = 440f - 4 * pitch
        var previous = StreamGridFade.alpha(x, 600f, column, peekThrough = true)
        while (x < 600f) {
            x += pitch
            val now = StreamGridFade.alpha(x, 600f, column, peekThrough = true)
            assertTrue(previous - now < 0.45f, "one column to the next: $previous -> $now at $x")
            previous = now
        }
    }

    @Test
    fun theFloorIsHighEnoughNotToReadAsDisabled() {
        assertTrue(StreamGridFade.PEEK_ALPHA >= 0.28f)
    }

    @Test
    fun itFadesAboveAndBelowTheColumnToo() {
        val above = StreamGridFade.alpha(600f, 400f - 30f, column, peekThrough = false)
        assertTrue(above in 0.01f..0.99f)
        assertEquals(1f, StreamGridFade.alpha(600f, 400f - StreamGridFade.FEATHER_DP, column, peekThrough = false))
        val below = StreamGridFade.alpha(600f, 900f + 30f, column, peekThrough = false)
        assertTrue(below in 0.01f..0.99f)
    }

    @Test
    fun peekThroughNeverMakesAnItemDarkerThanOff() {
        for (x in 100..900 step 20) {
            val peek = StreamGridFade.alpha(x.toFloat(), 600f, column, peekThrough = true)
            val off = StreamGridFade.alpha(x.toFloat(), 600f, column, peekThrough = false)
            assertTrue(peek >= off)
            assertTrue(peek in StreamGridFade.PEEK_ALPHA..1f)
        }
    }

    @Test
    fun presenceBringsTheFadeInAndOut() {
        assertEquals(1f, StreamGridFade.alpha(deepX, deepY, column, peekThrough = false, presence = 0f))
        assertEquals(0.5f, StreamGridFade.alpha(deepX, deepY, column, peekThrough = false, presence = 0.5f))
        assertEquals(0f, StreamGridFade.alpha(deepX, deepY, column, peekThrough = false, presence = 1f))
    }

    @Test
    fun anOpenCardTakesTheAppsBehindItOutToo() {
        assertEquals(0f, StreamGridFade.alpha(deepX, deepY, column, peekThrough = true, open = 1f))
        val half = StreamGridFade.alpha(deepX, deepY, column, peekThrough = true, open = 0.5f)
        assertTrue(half > 0f && half < StreamGridFade.PEEK_ALPHA)
        assertEquals(1f, StreamGridFade.alpha(100f, 100f, column, peekThrough = true, open = 1f))
    }
}

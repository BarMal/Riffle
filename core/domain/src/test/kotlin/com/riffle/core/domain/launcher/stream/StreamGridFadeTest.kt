package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamGridFadeTest {
    private val column = StreamColumnRect(left = 440f, top = 400f, right = 760f, bottom = 900f)

    @Test
    fun withNoColumnNothingFades() {
        assertEquals(1f, StreamGridFade.alpha(500f, 500f, null, peekThrough = true))
        assertEquals(1f, StreamGridFade.alpha(500f, 500f, StreamColumnRect(0f, 0f, 0f, 0f), peekThrough = false))
    }

    @Test
    fun underTheColumnAnItemKeepsOnlyWhatPeekThroughAllowsOrNothing() {
        assertEquals(StreamGridFade.PEEK_ALPHA, StreamGridFade.alpha(600f, 600f, column, peekThrough = true), 0.0001f)
        assertEquals(0f, StreamGridFade.alpha(600f, 600f, column, peekThrough = false))
    }

    @Test
    fun wellClearOfTheColumnAnItemIsUntouched() {
        assertEquals(1f, StreamGridFade.alpha(440f - StreamGridFade.FEATHER_DP, 600f, column, peekThrough = false))
        assertEquals(1f, StreamGridFade.alpha(100f, 100f, column, peekThrough = false))
    }

    @Test
    fun theFadeEasesWithoutAStepFromTheColumnsEdgeOutward() {
        var previous = StreamGridFade.alpha(440f, 600f, column, peekThrough = false)
        var x = 440f
        while (x > 440f - StreamGridFade.FEATHER_DP) {
            x -= 4f
            val now = StreamGridFade.alpha(x, 600f, column, peekThrough = false)
            assertTrue(now >= previous, "never darker further out: $now < $previous at $x")
            assertTrue(now - previous < 0.1f, "no hard cut: ${now - previous} at $x")
            previous = now
        }
        assertEquals(1f, StreamGridFade.alpha(440f - StreamGridFade.FEATHER_DP, 600f, column, peekThrough = false))
    }

    @Test
    fun itFadesAboveAndBelowTheColumnToo() {
        val above = StreamGridFade.alpha(600f, 400f - 30f, column, peekThrough = false)
        assertTrue(above in 0.01f..0.99f)
        assertEquals(1f, StreamGridFade.alpha(600f, 400f - StreamGridFade.FEATHER_DP, column, peekThrough = false))
    }

    @Test
    fun peekThroughNeverMakesAnItemDarkerThanOff() {
        for (x in 300..800 step 20) {
            val peek = StreamGridFade.alpha(x.toFloat(), 600f, column, peekThrough = true)
            val off = StreamGridFade.alpha(x.toFloat(), 600f, column, peekThrough = false)
            assertTrue(peek >= off)
            assertTrue(peek in StreamGridFade.PEEK_ALPHA..1f)
        }
    }

    @Test
    fun presenceBringsTheFadeInAndOut() {
        assertEquals(1f, StreamGridFade.alpha(600f, 600f, column, peekThrough = false, presence = 0f))
        assertEquals(0.5f, StreamGridFade.alpha(600f, 600f, column, peekThrough = false, presence = 0.5f))
        assertEquals(0f, StreamGridFade.alpha(600f, 600f, column, peekThrough = false, presence = 1f))
    }

    @Test
    fun anOpenCardTakesTheAppsBehindItOutToo() {
        assertEquals(0f, StreamGridFade.alpha(600f, 600f, column, peekThrough = true, open = 1f))
        val half = StreamGridFade.alpha(600f, 600f, column, peekThrough = true, open = 0.5f)
        assertTrue(half > 0f && half < StreamGridFade.PEEK_ALPHA)
        assertEquals(1f, StreamGridFade.alpha(100f, 100f, column, peekThrough = true, open = 1f))
    }
}

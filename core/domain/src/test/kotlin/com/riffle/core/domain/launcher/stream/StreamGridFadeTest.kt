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
    fun aShortFeatherLeavesAppsBeyondItUntouchedAndKeepsThePeekUnderTheCard() {
        // Round 7: with a card open the fade follows the card, 24 dp of feather, apps under it at the peek opacity.
        val feather = StreamGridFade.CARD_FEATHER_DP
        val under = StreamGridFade.alpha(deepX, deepY, column, peekThrough = true, featherDp = feather)
        assertEquals(StreamGridFade.PEEK_ALPHA, under, 0.001f)
        assertEquals(1f, StreamGridFade.alpha(440f - feather, 600f, column, peekThrough = true, featherDp = feather))
        val beyond = StreamGridFade.alpha(440f - feather - 1f, 600f, column, peekThrough = true, featherDp = feather)
        assertEquals(1f, beyond)
    }

    // "Around the cards"

    private val cell = StreamColumnRect(0f, 0f, 60f, 80f)

    @Test
    fun aCellUnderACardIsHiddenAndOneClearOfItIsUntouched() {
        val under = StreamColumnRect(-10f, 20f, 200f, 300f)
        assertEquals(0f, StreamGridFade.aroundFactor(cell, listOf(under)))
        // Touching the edge counts as meeting it.
        assertEquals(0f, StreamGridFade.aroundFactor(cell, listOf(StreamColumnRect(60f, 0f, 300f, 300f))))
        val clear = StreamColumnRect(60f + StreamGridFade.CARD_FEATHER_DP, 0f, 300f, 300f)
        assertEquals(1f, StreamGridFade.aroundFactor(cell, listOf(clear)))
        assertEquals(1f, StreamGridFade.aroundFactor(cell, emptyList()))
    }

    @Test
    fun aCellBesideOrAboveACardKeepsItsFadeAndTheFeatherIsSmoothAndMonotonic() {
        var previous = 0f
        for (gap in 0..StreamGridFade.CARD_FEATHER_DP.toInt() + 6) {
            val card = StreamColumnRect(60f + gap, 0f, 400f, 300f)
            val now = StreamGridFade.aroundFactor(cell, listOf(card))
            assertTrue(now >= previous, "the factor fell at a gap of $gap")
            assertTrue(now - previous < 0.2f, "the factor stepped at a gap of $gap")
            previous = now
        }
        assertEquals(1f, previous)
        // Above a card (a gap in y) is the same ramp.
        val below = StreamColumnRect(0f, 80f + 12f, 400f, 300f)
        val half = StreamGridFade.aroundFactor(cell, listOf(below))
        assertTrue(half > 0f && half < 1f)
    }

    @Test
    fun theNearestCardDecidesWhenThereAreSeveral() {
        val near = StreamColumnRect(0f, 80f, 100f, 200f)
        val far = StreamColumnRect(0f, 500f, 100f, 600f)
        assertEquals(0f, StreamGridFade.aroundFactor(cell, listOf(far, near)))
    }
}

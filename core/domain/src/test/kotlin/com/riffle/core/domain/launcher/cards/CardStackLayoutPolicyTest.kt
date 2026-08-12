package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardStackLayoutPolicyTest {
    private val policy = CardStackLayoutPolicy()

    @Test
    fun emptyStackHasNoEntries() {
        assertEquals(emptyList(), policy.entries(cardCount = 0, activeIndex = 0))
    }

    @Test
    fun firstActiveCardKeepsFocusedCardOnTop() {
        val entries = policy.entries(cardCount = 5, activeIndex = 0)

        assertEquals(listOf(3, 2, 1, 0), entries.map { entry -> entry.cardIndex })
        assertEquals(listOf(0, 1, 2, 3), entries.map { entry -> entry.order })
        assertEquals(
            CardStackLayoutEntry(
                cardIndex = 0,
                order = 3,
                depth = 0,
                scale = 1f,
                offset = 0f,
                verticalOffset = 0f,
                rotationDegrees = 0f,
                alpha = 1f,
            ),
            entries.last(),
        )
    }

    @Test
    fun middleActiveCardOrdersVisibleNeighborsBehindFocusedCard() {
        val entries = policy.entries(cardCount = 7, activeIndex = 3)

        assertEquals(listOf(0, 6, 1, 5, 2, 4, 3), entries.map { entry -> entry.cardIndex })
        assertEquals(listOf(3, 3, 2, 2, 1, 1, 0), entries.map { entry -> entry.depth })
        assertEquals(
            listOf(-72f, 72f, -48f, 48f, -24f, 24f, 0f),
            entries.map { entry -> entry.offset },
        )
        assertEquals(
            listOf(0.82f, 0.82f, 0.88f, 0.88f, 0.94f, 0.94f, 1f),
            entries.map { entry -> entry.scale },
        )
        // The outermost visible depth (3, at maxVisibleDepth itself) fades fully to transparent on
        // top of alphaStep's own dim -- see CardStackLayoutPolicy.edgeFadeMultiplier -- rather than
        // staying clearly opaque right up to wherever a caller's own viewport clips it.
        assertFloatListEquals(
            listOf(0f, 0f, 0.45333f, 0.45333f, 0.84f, 0.84f, 1f),
            entries.map { entry -> entry.alpha },
        )
    }

    @Test
    fun fractionalActiveIndexContinuouslyInterpolatesEveryEntrysPose() {
        // activeIndex sitting exactly halfway between cardIndex 2 and 3 -- every card's own
        // depth-relative pose should land exactly halfway between where the two neighboring
        // integer activeIndex calls would put it, using the same formulas either way. This is
        // what lets a live drag drive this same call every frame (see the Float overload's own
        // doc) instead of only recomputing once a drag settles on a new integer index.
        val entries = policy.entries(cardCount = 5, activeIndex = 2.5f)

        assertEquals(listOf(0, 1, 4, 2, 3), entries.map { entry -> entry.cardIndex })
        assertEquals(listOf(0, 1, 2, 3, 4), entries.map { entry -> entry.order })
        assertFloatListEquals(
            listOf(0.85f, 0.91f, 0.91f, 0.97f, 0.97f),
            entries.map { entry -> entry.scale },
        )
        assertFloatListEquals(
            listOf(-60f, -36f, 36f, -12f, 12f),
            entries.map { entry -> entry.offset },
        )
        // depth=2.5 sits past the edge-fade start (maxVisibleDepth * 0.5 = 1.5), so it fades on
        // top of alphaStep's own dim -- see CardStackLayoutPolicy.edgeFadeMultiplier.
        assertFloatListEquals(
            listOf(0.2f, 0.76f, 0.76f, 0.92f, 0.92f),
            entries.map { entry -> entry.alpha },
        )
    }

    @Test
    fun integerActiveIndexOverloadMatchesTheEquivalentFractionalCall() {
        assertEquals(
            policy.entries(cardCount = 7, activeIndex = 3),
            policy.entries(cardCount = 7, activeIndex = 3f),
        )
    }

    @Test
    fun curveEasesSmoothlyTowardItsConfiguredPeakInsteadOfBeingCrushedByDistanceSquared() {
        // Isolates the curve term (verticalOffsetStep=0f) across a deep, 6-visible-depth stack.
        val policy = CardStackLayoutPolicy(maxVisibleDepth = 6, verticalOffsetStep = 0f, curveStep = 60f)
        val entries = policy.entries(cardCount = 13, activeIndex = 6)
        val midDepthOffset = entries.first { entry -> entry.cardIndex == 9 }.verticalOffset // depth 3 of 6
        val peakOffset = entries.first { entry -> entry.cardIndex == 12 }.verticalOffset // depth 6 of 6

        // A squared-distance falloff (the old formula: curveStep * signedDistance^2) would put the
        // halfway-depth card at only a quarter of the configured peak. This smootherstep-eased
        // curve reaches exactly half of it there instead, so the cascade stays visibly curved
        // through the middle of the stack rather than concentrating almost all of it into the
        // outermost card or two.
        assertEquals(60f, kotlin.math.abs(peakOffset), absoluteTolerance = 0.001f)
        assertEquals(30f, kotlin.math.abs(midDepthOffset), absoluteTolerance = 0.001f)
    }

    @Test
    fun verticalOffsetDirectionFlipsOrDisablesVerticalFanIndependentlyOfHorizontalFan() {
        val forward = CardStackLayoutPolicy(verticalOffsetStep = 10f, curveStep = 2f, verticalOffsetDirection = 1f)
        val reversed = CardStackLayoutPolicy(verticalOffsetStep = 10f, curveStep = 2f, verticalOffsetDirection = -1f)
        val disabled = CardStackLayoutPolicy(verticalOffsetStep = 10f, curveStep = 2f, verticalOffsetDirection = 0f)

        // Excludes cardIndex 1 (the focused card, signedDistance 0): its verticalOffset is always
        // exactly zero regardless of direction.
        fun verticalOffsetsByCardIndex(policy: CardStackLayoutPolicy): List<Float> =
            policy.entries(cardCount = 3, activeIndex = 1)
                .filter { entry -> entry.cardIndex != 1 }
                .sortedBy { entry -> entry.cardIndex }
                .map { entry -> entry.verticalOffset }

        // cardIndex 0 is one step before the focused card (signedDistance -1, depth 1), cardIndex
        // 2 is one step after (signedDistance +1, depth 1) -- verticalOffsetDirection=1 (the
        // default, matching every prior caller's only behavior) keeps earlier cards fanning up,
        // later cards fanning down; -1 flips that; 0 disables vertical fan/curve entirely
        // regardless of the step values. Depth 1 of a default maxVisibleDepth=3 stack sits at
        // curveProgress(1/3) = 17/81, so each value is verticalOffsetStep*signedDistance +/-
        // curveStep*(17/81) = +/-(10 + 34/81).
        assertFloatListEquals(listOf(-10.41975f, 10.41975f), verticalOffsetsByCardIndex(forward))
        assertFloatListEquals(listOf(10.41975f, -10.41975f), verticalOffsetsByCardIndex(reversed))
        assertFloatListEquals(listOf(0f, 0f), verticalOffsetsByCardIndex(disabled))
    }

    @Test
    fun lastActiveCardKeepsTrailingStackInBounds() {
        val entries = policy.entries(cardCount = 5, activeIndex = 4)

        assertEquals(listOf(1, 2, 3, 4), entries.map { entry -> entry.cardIndex })
        assertEquals(listOf(3, 2, 1, 0), entries.map { entry -> entry.depth })
        assertEquals(listOf(-72f, -48f, -24f, 0f), entries.map { entry -> entry.offset })
        assertEquals(3, entries.last().order)
    }

    @Test
    fun maxVisibleDepthLimitsStackEntries() {
        val shallowPolicy = CardStackLayoutPolicy(maxVisibleDepth = 2)
        val entries = shallowPolicy.entries(cardCount = 9, activeIndex = 4)

        assertEquals(listOf(2, 6, 3, 5, 4), entries.map { entry -> entry.cardIndex })
        assertTrue(entries.none { entry -> entry.cardIndex == 1 })
        assertTrue(entries.none { entry -> entry.cardIndex == 7 })
        assertEquals(2, entries.maxOf { entry -> entry.depth })
    }

    @Test
    fun reducedMotionKeepsOrderingAndDepthWhileLimitingMovement() {
        val standardEntries = policy.entries(cardCount = 7, activeIndex = 3)
        val reducedMotionEntries = policy.entries(cardCount = 7, activeIndex = 3, reducedMotion = true)

        assertEquals(
            standardEntries.map { entry -> entry.cardIndex to entry.order },
            reducedMotionEntries.map { entry -> entry.cardIndex to entry.order },
        )
        assertEquals(
            standardEntries.map { entry -> entry.depth },
            reducedMotionEntries.map { entry -> entry.depth },
        )
        assertEquals(
            listOf(-6f, 6f, -4f, 4f, -2f, 2f, 0f),
            reducedMotionEntries.map { entry -> entry.offset },
        )
        assertFloatListEquals(
            listOf(0.97f, 0.97f, 0.98f, 0.98f, 0.99f, 0.99f, 1f),
            reducedMotionEntries.map { entry -> entry.scale },
        )
        assertTrue(
            reducedMotionEntries.maxOf { entry -> kotlin.math.abs(entry.offset) } <
                standardEntries.maxOf { entry -> kotlin.math.abs(entry.offset) },
        )
    }

    @Test
    fun profilesExposeNamedDeterministicGeometry() {
        assertEquals(
            setOf(
                CardStackLayoutProfile.DECK,
                CardStackLayoutProfile.FAN,
                CardStackLayoutProfile.VERTICAL,
                CardStackLayoutProfile.CAROUSEL,
                CardStackLayoutProfile.COMPACT,
            ),
            CardStackLayoutProfile.entries.toSet(),
        )
        assertEquals(
            listOf(-36f, 36f, 0f),
            CardStackLayoutPolicy.forProfile(CardStackLayoutProfile.VERTICAL).entries(cardCount = 3, activeIndex = 1)
                .map { entry -> entry.verticalOffset },
        )
    }

    private fun assertFloatListEquals(
        expected: List<Float>,
        actual: List<Float>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedValue, actualValue) ->
            assertEquals(expectedValue, actualValue, absoluteTolerance = 0.0001f)
        }
    }
}

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
        assertFloatListEquals(
            listOf(0.52f, 0.52f, 0.68f, 0.68f, 0.84f, 0.84f, 1f),
            entries.map { entry -> entry.alpha },
        )
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

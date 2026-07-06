package com.riffle.core.domain.launcher.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTokensTest {
    @Test
    fun normalizedSearchTokensTrimCollapseWhitespaceAndCaseFold() {
        assertEquals(
            listOf("google", "maps"),
            normalizedSearchTokens("  Google   MAPS\t"),
        )
    }

    @Test
    fun normalizedSearchTokensReturnEmptyForBlankQuery() {
        assertEquals(emptyList(), normalizedSearchTokens(" \t "))
    }

    @Test
    fun searchAcronymUsesLowercaseAlphanumericTokenStarts() {
        assertEquals("gm", "Google Maps".searchAcronym())
        assertEquals("ts", "take-selfie".searchAcronym())
    }

    @Test
    fun containsAllSearchTokensSupportsSubstringMatching() {
        assertTrue("google maps".containsAllSearchTokens(listOf("goo", "map")))
        assertTrue(listOf("com.example.weather", "work").containsAllSearchTokens(listOf("weather", "work")))
        assertFalse(listOf("com.example.weather", "personal").containsAllSearchTokens(listOf("weather", "work")))
    }
}

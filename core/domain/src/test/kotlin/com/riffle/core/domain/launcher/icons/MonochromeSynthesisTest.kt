package com.riffle.core.domain.launcher.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonochromeSynthesisTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val clear = 0

    @Test
    fun aLightMarkOnADarkFieldIsTheMark() {
        // Mostly dark opaque pixels, two light ones.
        val pixels = intArrayOf(black, black, black, white, white, clear)

        val coverage = MonochromeSynthesis.coverage(pixels)

        assertEquals(listOf(0, 0, 0, 255, 255, 0), coverage.toList())
    }

    @Test
    fun aDarkMarkOnALightFieldIsTheMark() {
        val pixels = intArrayOf(white, white, white, black, clear)

        assertEquals(listOf(0, 0, 0, 255, 0), MonochromeSynthesis.coverage(pixels).toList())
    }

    @Test
    fun aFlatSymbolKeepsItsWholeSilhouette() {
        assertEquals(listOf(255, 255, 0), MonochromeSynthesis.coverage(intArrayOf(white, white, clear)).toList())
        assertEquals(listOf(255, 0), MonochromeSynthesis.coverage(intArrayOf(black, clear)).toList())
    }

    @Test
    fun transparencyScalesTheCoverageAndAnEmptyIconHasNone() {
        assertEquals(listOf(0, 0), MonochromeSynthesis.coverage(intArrayOf(clear, clear)).toList())
        val halfWhite = 0x80FFFFFF.toInt()
        val coverage = MonochromeSynthesis.coverage(intArrayOf(black, black, black, halfWhite))
        assertTrue(coverage[3] in 120..130, "${coverage[3]}")
    }

    @Test
    fun anEmptyArrayIsFine() {
        assertEquals(0, MonochromeSynthesis.coverage(IntArray(0)).size)
    }
}

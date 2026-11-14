package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowseCardHeightTest {
    private val line = BrowseCardHeight.LINE_DP

    @Test
    fun `a one line message is the minimum, the header and one line, not the old fixed height`() {
        val height = BrowseCardHeight.notification(bodyDp = line)
        assertEquals(BrowseCardHeight.MIN_DP, height)
        assertEquals(88f, height)
        assertTrue(height < StreamCardStackSpec.CARD_HEIGHT_DP)
    }

    @Test
    fun `the card grows by a line for each line of body up to four`() {
        val heights = (1..BrowseCardHeight.MAX_BODY_LINES).map { BrowseCardHeight.notification(bodyDp = line * it) }
        assertEquals(listOf(88f, 108f, 128f, 148f), heights)
        assertTrue(heights.last() <= BrowseCardHeight.MAX_DP)
    }

    @Test
    fun `it never goes below the minimum or above the maximum`() {
        assertEquals(BrowseCardHeight.MIN_DP, BrowseCardHeight.notification(bodyDp = 0f))
        assertEquals(BrowseCardHeight.MAX_DP, BrowseCardHeight.notification(bodyDp = 400f))
        assertEquals(
            BrowseCardHeight.MAX_WITH_ACTIONS_DP,
            BrowseCardHeight.notification(bodyDp = 400f, hasActions = true),
        )
    }

    @Test
    fun `a thumbnail keeps its room beside a short body`() {
        assertEquals(52f + 8f + 64f + 8f, BrowseCardHeight.notification(bodyDp = line, hasThumbnail = true))
        // A body taller than the thumbnail decides.
        assertEquals(
            BrowseCardHeight.notification(bodyDp = line * 4),
            BrowseCardHeight.notification(bodyDp = line * 4, hasThumbnail = true),
        )
    }

    @Test
    fun `a live notification's buttons add their row and four lines still fit with them`() {
        assertEquals(52f + 8f + line + 8f + 48f + 8f, BrowseCardHeight.notification(bodyDp = line, hasActions = true))
        val full = BrowseCardHeight.notification(bodyDp = line * 4, hasActions = true)
        assertEquals(52f + 8f + 80f + 8f + 48f + 8f, full)
        assertTrue(full <= BrowseCardHeight.MAX_WITH_ACTIONS_DP)
        // Buttons with no body at all: the header and the buttons.
        assertEquals(52f + 8f + 48f + 8f, BrowseCardHeight.notification(bodyDp = 0f, hasActions = true))
    }

    @Test
    fun `the fixed parts grow with the font scale and the measured body does not grow twice`() {
        val scaled = BrowseCardHeight.notification(bodyDp = 26f, scale = 1.3f)
        assertEquals(maxOf(52f * 1.3f + 8f + 26f + 8f, BrowseCardHeight.MIN_DP * 1.3f), scaled, 0.01f)
        assertEquals(BrowseCardHeight.MAX_DP * 1.3f, BrowseCardHeight.notification(bodyDp = 900f, scale = 1.3f), 0.01f)
    }
}

package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class PageOverviewCardStateTest {
    @Test
    fun dropTargetRoundsToTheNearestCardAndStaysWithinTheOverview() {
        assertEquals(
            2,
            pageOverviewDropTargetIndex(
                index = 1,
                pageCount = 4,
                dragDistancePx = 92f,
                cardStepPx = 100f,
            ),
        )
        assertEquals(
            0,
            pageOverviewDropTargetIndex(
                index = 1,
                pageCount = 4,
                dragDistancePx = -600f,
                cardStepPx = 100f,
            ),
        )
        assertEquals(
            3,
            pageOverviewDropTargetIndex(
                index = 2,
                pageCount = 4,
                dragDistancePx = 600f,
                cardStepPx = 100f,
            ),
        )
    }
}

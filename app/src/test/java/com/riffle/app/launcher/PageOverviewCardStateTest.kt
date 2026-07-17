package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class PageOverviewCardStateTest {
    @Test
    fun reflowStartsAtThePreviousCardPosition() {
        assertEquals(
            -192f,
            pageOverviewReflowStartOffsetPx(
                previousIndex = 0,
                newIndex = 1,
                cardStepPx = 192f,
            ),
        )
        assertEquals(
            384f,
            pageOverviewReflowStartOffsetPx(
                previousIndex = 3,
                newIndex = 1,
                cardStepPx = 192f,
            ),
        )
    }

    @Test
    fun reducedMotionReflowStartsAtTheCurrentPosition() {
        assertEquals(
            0f,
            pageOverviewReflowInitialOffsetPx(
                previousIndex = 3,
                newIndex = 1,
                cardStepPx = 192f,
                reducedMotion = true,
            ),
        )
    }

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
        assertEquals(
            3,
            pageOverviewDropTargetIndex(
                index = 1,
                pageCount = 4,
                dragDistancePx = 10f,
                scrollDistancePx = 220f,
                cardStepPx = 100f,
            ),
        )
    }

    @Test
    fun draggedCardTranslationCompensatesForLazyRowScroll() {
        assertEquals(
            230f,
            pageOverviewDraggedCardTranslationPx(
                dragDistancePx = 30f,
                scrollDistancePx = 200f,
            ),
        )
        assertEquals(
            -180f,
            pageOverviewDraggedCardTranslationPx(
                dragDistancePx = 20f,
                scrollDistancePx = -200f,
            ),
        )
    }

    @Test
    fun projectedPositionMovesCardsOutOfTheDraggedCardsTargetRange() {
        val preview = PageOverviewDragPreview(sourceIndex = 1, targetIndex = 3)

        assertEquals(0, pageOverviewProjectedVisualIndex(pageIndex = 0, dragPreview = preview))
        assertEquals(1, pageOverviewProjectedVisualIndex(pageIndex = 1, dragPreview = preview))
        assertEquals(1, pageOverviewProjectedVisualIndex(pageIndex = 2, dragPreview = preview))
        assertEquals(2, pageOverviewProjectedVisualIndex(pageIndex = 3, dragPreview = preview))
        assertEquals(4, pageOverviewProjectedVisualIndex(pageIndex = 4, dragPreview = preview))
    }

    @Test
    fun projectedPositionMovesCardsRightWhenTheDraggedCardMovesLeft() {
        val preview = PageOverviewDragPreview(sourceIndex = 3, targetIndex = 1)

        assertEquals(0, pageOverviewProjectedVisualIndex(pageIndex = 0, dragPreview = preview))
        assertEquals(2, pageOverviewProjectedVisualIndex(pageIndex = 1, dragPreview = preview))
        assertEquals(3, pageOverviewProjectedVisualIndex(pageIndex = 2, dragPreview = preview))
        assertEquals(3, pageOverviewProjectedVisualIndex(pageIndex = 3, dragPreview = preview))
    }

    @Test
    fun edgeScrollDirectionUsesTheActualViewportEdges() {
        assertEquals(
            -1,
            pageOverviewViewportEdgeScrollDirection(
                pointerPositionPx = 55f,
                viewportStartPx = 0f,
                viewportEndPx = 360f,
            ),
        )
        assertEquals(
            0,
            pageOverviewViewportEdgeScrollDirection(
                pointerPositionPx = 180f,
                viewportStartPx = 0f,
                viewportEndPx = 360f,
            ),
        )
        assertEquals(
            1,
            pageOverviewViewportEdgeScrollDirection(
                pointerPositionPx = 304f,
                viewportStartPx = 0f,
                viewportEndPx = 360f,
            ),
        )
    }
}

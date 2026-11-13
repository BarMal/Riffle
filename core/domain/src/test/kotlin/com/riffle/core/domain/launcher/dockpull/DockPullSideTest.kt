package com.riffle.core.domain.launcher.dockpull

import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullSideTest {
    // A horizontal run 0..400 whose dynamic section is 300..400 (at the end), or 0..100 (at the start, mirrored).
    @Test
    fun aTouchPastTheDynamicSectionsNearEdgeIsOnTheDynamicSide() {
        assertEquals(DockPullSide.DYNAMIC, side(320f, dynamicStart = 300f, dynamicEnd = 390f))
        assertEquals(DockPullSide.DYNAMIC, side(300f, dynamicStart = 300f, dynamicEnd = 390f))
        assertEquals(DockPullSide.DYNAMIC, side(398f, dynamicStart = 300f, dynamicEnd = 390f))
    }

    @Test
    fun aTouchBeforeItIsPinned() {
        assertEquals(DockPullSide.PINNED, side(299f, dynamicStart = 300f, dynamicEnd = 390f))
        assertEquals(DockPullSide.PINNED, side(10f, dynamicStart = 300f, dynamicEnd = 390f))
    }

    @Test
    fun aMirroredDockWithTheSectionAtTheStartReadsTheSame() {
        assertEquals(DockPullSide.DYNAMIC, side(5f, dynamicStart = 10f, dynamicEnd = 100f))
        assertEquals(DockPullSide.DYNAMIC, side(100f, dynamicStart = 10f, dynamicEnd = 100f))
        assertEquals(DockPullSide.PINNED, side(101f, dynamicStart = 10f, dynamicEnd = 100f))
    }

    @Test
    fun noDynamicSectionMeansEverythingIsPinned() {
        assertEquals(DockPullSide.PINNED, side(350f, dynamicStart = null, dynamicEnd = null))
    }

    @Test
    fun thePinnedSidePullAlwaysOpensTheLibrary() {
        listOf(false, true).forEach { shown ->
            assertEquals(DockPullDestination.LIBRARY, dockPullDestinationFor(DockPullSide.PINNED, shown, true))
        }
    }

    @Test
    fun theDynamicSidePullOpensTheStreamAndFromTheStreamReturnsHome() {
        assertEquals(DockPullDestination.STREAM, dockPullDestinationFor(DockPullSide.DYNAMIC, false, true))
        assertEquals(DockPullDestination.HOME, dockPullDestinationFor(DockPullSide.DYNAMIC, true, true))
    }

    @Test
    fun withoutAStreamTheDynamicSideLeadsToTheLibrary() {
        assertEquals(DockPullDestination.LIBRARY, dockPullDestinationFor(DockPullSide.DYNAMIC, false, false))
    }

    private fun side(
        position: Float,
        dynamicStart: Float?,
        dynamicEnd: Float?,
    ) = dockPullSideAt(position, dockStart = 0f, dockEnd = 400f, dynamicStart = dynamicStart, dynamicEnd = dynamicEnd)

    private val entries =
        listOf(
            DockPullEntryExtent("live:chat:personal", 300f, 348f),
            DockPullEntryExtent("live:mail:personal", 352f, 400f),
            DockPullEntryExtent("stream", 404f, 452f),
        )

    @Test
    fun aTouchOnAnEntryFindsThatEntryAndOneInTheGapOrElsewhereFindsNone() {
        assertEquals("live:chat:personal", dockPullEntryAt(320f, entries))
        assertEquals("live:mail:personal", dockPullEntryAt(352f, entries))
        assertEquals("stream", dockPullEntryAt(420f, entries))
        assertEquals(null, dockPullEntryAt(350f, entries))
        assertEquals(null, dockPullEntryAt(100f, entries))
        assertEquals(null, dockPullEntryAt(452f, entries))
    }

    @Test
    fun aPullThatOpensTheStreamLandsOnTheEntryItStartedOnAndNothingElseLandsAnywhere() {
        assertEquals("live:chat", dockPullLandingEntry(DockPullDestination.STREAM, "live:chat"))
        assertEquals(null, dockPullLandingEntry(DockPullDestination.STREAM, null))
        assertEquals(null, dockPullLandingEntry(DockPullDestination.HOME, "live:chat"))
        assertEquals(null, dockPullLandingEntry(DockPullDestination.LIBRARY, "live:chat"))
    }
}

package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenCardMotionTest {
    private fun run(
        from: OpenCardSpringState,
        target: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): List<OpenCardSpringState> {
        val frames = mutableListOf(from)
        var state = from
        var guard = 0
        while (!OpenCardSpring.isSettled(state, target) && guard++ < 600) {
            state = OpenCardSpring.advance(state, target, 1f / 60f, tuning)
            frames += state
        }
        return frames
    }

    @Test
    fun theGrowthNeverOvershootsAndEndsAtOne() {
        val frames = run(OpenCardSpringState(0f), 1f)
        assertTrue(frames.all { it.progress <= 1f + 1e-6f && it.progress >= 0f })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.progress >= a.progress - 1e-6f })
        assertTrue(OpenCardSpring.isSettled(frames.last(), 1f))
    }

    @Test
    fun theShrinkNeverUndershootsAndEndsAtZero() {
        val frames = run(OpenCardSpringState(1f), 0f)
        assertTrue(frames.all { it.progress >= -1e-6f && it.progress <= 1f })
        assertTrue(OpenCardSpring.isSettled(frames.last(), 0f))
    }

    @Test
    fun aSlowerSpeedSettingTakesLonger() {
        val fast = run(OpenCardSpringState(0f), 1f, DockMotionTuning(speedMillis = 150)).size
        val slow = run(OpenCardSpringState(0f), 1f, DockMotionTuning(speedMillis = 400)).size
        assertTrue(slow > fast, "$slow vs $fast")
    }

    @Test
    fun theSameInputGivesTheSamePathWhateverTheFrameRate() {
        val sixty = (1..30).fold(OpenCardSpringState(0f)) { s, _ -> OpenCardSpring.advance(s, 1f, 1f / 60f) }
        val ninety = (1..45).fold(OpenCardSpringState(0f)) { s, _ -> OpenCardSpring.advance(s, 1f, 1f / 90f) }
        assertEquals(sixty.progress, ninety.progress, 0.01f)
    }

    @Test
    fun aStalledFrameCannotExplodeTheSimulation() {
        val after = OpenCardSpring.advance(OpenCardSpringState(0f), 1f, seconds = 30f)
        assertTrue(after.progress in 0f..1f)
    }

    @Test
    fun theReducedMotionFadeRisesFromItsStartToFullOverTheReducedFade() {
        assertEquals(DockMotionConstants.OPEN_CARD_REDUCED_FADE_FROM, OpenCardSpring.reducedFadeAlpha(0f), 1e-6f)
        assertEquals(1f, OpenCardSpring.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS), 1e-6f)
        assertTrue(OpenCardSpring.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS / 2) in 0.4f..1f)
    }

    private fun frame(
        progress: Float,
        placement: OpenCardPlacement,
        strips: Float = 100f,
    ) = OpenCardLayout.frame(
        progress = progress,
        areaHeightPx = 1000f,
        bottomInsetPx = 50f,
        browseHeightPx = 300f,
        fullHeightPx = 500f,
        stripsPx = strips,
        placement = placement,
    )

    @Test
    fun atRestBrowsedTheCardIsWhereItsBrowseCardStands() {
        val frame = frame(0f, OpenCardPlacement.CENTRE)
        assertEquals(300f, frame.heightPx)
        // Just above the Dock's gap: the bottom of the area less the gap less the card.
        assertEquals(1000f - 50f - 300f, frame.topPx)
    }

    @Test
    fun openAtTheCentreItSitsMidwayBetweenTheStripsAndTheDock() {
        val frame = frame(1f, OpenCardPlacement.CENTRE)
        assertEquals(500f, frame.heightPx)
        // The room is 100 (below the strips) to 950 (above the Dock's gap): 850 tall; a 500 card leaves 175 each side.
        assertEquals(100f + 175f, frame.topPx)
    }

    @Test
    fun openJustAboveTheDockItsBottomIsTheDocksGap() {
        val above = frame(1f, OpenCardPlacement.ABOVE_DOCK)
        assertEquals(950f, above.topPx + above.heightPx)
    }

    @Test
    fun centreAndAboveTheDockPutAShortOpenCardInDifferentPlaces() {
        // The setting is real folded: a card shorter than the room sits higher at the centre than above the Dock.
        val centre = frame(1f, OpenCardPlacement.CENTRE)
        val above = frame(1f, OpenCardPlacement.ABOVE_DOCK)
        assertEquals(centre.heightPx, above.heightPx)
        assertTrue(centre.topPx < above.topPx)
    }

    @Test
    fun aCardTallerThanTheRoomIsHeldToItAndNeverCoversTheStrips() {
        val tall =
            OpenCardLayout.frame(
                1f,
                1000f,
                50f,
                300f,
                fullHeightPx = 5000f,
                stripsPx = 168f,
                placement = OpenCardPlacement.CENTRE,
            )
        assertEquals(1000f - 50f - 168f, tall.heightPx)
        assertEquals(168f, tall.topPx)
    }

    @Test
    fun theGrowthIsContinuousFromBrowseToOpen() {
        val heights = (0..10).map { frame(it / 10f, OpenCardPlacement.CENTRE).heightPx }
        assertTrue(heights.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(300f, heights.first())
        assertEquals(500f, heights.last())
    }

    @Test
    fun theStripsTakeAPitchPerCardBehindAndNoMoreThanThePeeksThereAre() {
        assertEquals(0f, OpenCardLayout.stripsPx(0, 56f))
        assertEquals(112f, OpenCardLayout.stripsPx(2, 56f))
        assertEquals(StreamCardStackSpec.VISIBLE_PEEKS * 56f, OpenCardLayout.stripsPx(9, 56f))
    }

    @Test
    fun theStripsStandOnlyWhereTheyLeaveTheOpenCardItsRoom() {
        // A tall area: three strips (168) and a 240 card fit with room to spare.
        assertTrue(OpenCardLayout.stripsFit(900f, 50f, 168f, 240f))
        // The keyboard is up: 400 of area leaves 182 for the card under three strips, so they are not drawn.
        assertEquals(false, OpenCardLayout.stripsFit(400f, 50f, 168f, 240f))
        // Exactly enough is enough; with no strips wanted the card needs only its own room.
        assertTrue(OpenCardLayout.stripsFit(458f, 50f, 168f, 240f))
        assertTrue(OpenCardLayout.stripsFit(300f, 50f, 0f, 240f))
    }

    // Open card polish: it grows from the collapsed front card's own rectangle and takes the room there is ------------

    private fun shared(
        progress: Float,
        placement: OpenCardPlacement = OpenCardPlacement.ABOVE_DOCK,
        full: Float = 5000f,
        strips: Float = 100f,
        below: Float = 0f,
    ) = OpenCardLayout.frame(
        progress = progress,
        areaHeightPx = 1000f,
        bottomInsetPx = 50f,
        browseHeightPx = 300f,
        fullHeightPx = full,
        stripsPx = strips,
        placement = placement,
        // The page keeps 140 under the stack, so the collapsed front card's bottom is 810, not 950.
        restBottomPx = 810f,
        belowPx = below,
    )

    @Test
    fun theOpenCardStartsExactlyWhereTheCollapsedFrontCardStands() {
        for (placement in OpenCardPlacement.entries) {
            val frame = shared(0f, placement)
            assertEquals(810f, frame.topPx + frame.heightPx, "$placement: bottom")
            assertEquals(300f, frame.heightPx, "$placement: height")
        }
    }

    @Test
    fun aCardWithMoreToSayThanFitsTakesAllTheRoomUnderItsStripsAndAboveTheDock() {
        val open = shared(1f)
        assertEquals(100f, open.topPx)
        assertEquals(950f, open.topPx + open.heightPx)
    }

    @Test
    fun theCardsInFrontOfItTakeTheirRoomOffTheBottom() {
        val open = shared(1f, below = 120f)
        assertEquals(830f, open.topPx + open.heightPx)
        assertEquals(100f, open.topPx)
    }

    @Test
    fun aCardWithLittleToSayKeepsItsContentHeightButIsNeverShorterThanItsBrowseCard() {
        assertEquals(500f, shared(1f, full = 500f).heightPx)
        val short = shared(1f, full = 120f)
        assertEquals(300f, short.heightPx)
        assertEquals(950f, short.topPx + short.heightPx)
    }

    private fun oneWay(values: List<Float>): Boolean =
        values.zipWithNext().all { (a, b) -> b <= a + 1e-3f } || values.zipWithNext().all { (a, b) -> b >= a - 1e-3f }

    @Test
    fun topAndBottomEachMoveOneWayOnlyWhateverThePlacement() {
        for (placement in OpenCardPlacement.entries) {
            for (full in listOf(120f, 500f, 5000f)) {
                val frames = (0..40).map { shared(it / 40f, placement, full) }
                val tops = frames.map { it.topPx }
                val bottoms = frames.map { it.topPx + it.heightPx }
                assertTrue(
                    oneWay(tops),
                    "$placement $full: top reversed $tops",
                )
                assertTrue(
                    oneWay(bottoms),
                    "$placement $full: bottom reversed $bottoms",
                )
                assertTrue(frames.map { it.heightPx }.zipWithNext().all { (a, b) -> b >= a - 1e-3f })
            }
        }
    }
}

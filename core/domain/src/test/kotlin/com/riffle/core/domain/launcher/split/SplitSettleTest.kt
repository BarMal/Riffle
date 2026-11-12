package com.riffle.core.domain.launcher.split

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitSettleTest {
    private val window = SplitWindow(widthDp = 840f, heightDp = 900f, dockExtentDp = 96f)

    @Test
    fun stoppingPartwayLeavesTheSplit() {
        listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f).forEach { reveal ->
            assertEquals(
                SplitMode.SPLIT,
                SplitSettle.settlePull(reveal, 0f, 200f, SplitMode.HOME_ONLY, true),
                "at $reveal",
            )
        }
    }

    @Test
    fun aShortPullReturnsToHomeAndALongOneReachesTheStream() {
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settlePull(0.15f, 0f, 120f, SplitMode.HOME_ONLY, true))
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settlePull(0.9f, 0f, 700f, SplitMode.HOME_ONLY, true))
    }

    @Test
    fun velocityCarriesTheReleaseToTheNextState() {
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settlePull(0.5f, 4f, 300f, SplitMode.HOME_ONLY, true))
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settlePull(0.4f, -3f, 200f, SplitMode.HOME_ONLY, true))
        assertEquals(SplitMode.SPLIT, SplitSettle.settlePull(0.2f, 1.5f, 200f, SplitMode.HOME_ONLY, true))
    }

    @Test
    fun aTwitchOnTheDockKeepsWhereItStarted() {
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settlePull(0.005f, 9f, 2f, SplitMode.HOME_ONLY, true))
        assertEquals(SplitMode.SPLIT, SplitSettle.settlePull(0.5f, -9f, 1f, SplitMode.SPLIT, true))
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settlePull(1f, 9f, 0f, SplitMode.STREAM_ONLY, true))
    }

    @Test
    fun withoutRoomThereAreOnlyTwoStates() {
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settlePull(0.4f, 0f, 200f, SplitMode.HOME_ONLY, false))
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settlePull(0.6f, 0f, 200f, SplitMode.HOME_ONLY, false))
    }

    @Test
    fun theDividerSettlesToTheNearestOfTheThreeStates() {
        assertEquals(SplitMode.SPLIT, SplitSettle.settleDivider(window, 0.4f, 0f).mode)
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settleDivider(window, 0.05f, 0f).mode)
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settleDivider(window, 0.95f, 0f).mode)
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.settleDivider(window, 0.7f, 2f).mode)
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.settleDivider(window, 0.3f, -2f).mode)
    }

    @Test
    fun aSplitRestsWhereItWasLetGoWithinTheLimits() {
        val target = SplitSettle.settleDivider(window, 0.45f, 0f)
        assertEquals(0.45f, target.share, 0.0001f)
        val clamped = SplitSettle.settleDivider(window, 0.28f, 0f)
        assertEquals(SplitGeometry.limits(window).minShare, clamped.share, 0.001f)
    }

    @Test
    fun theHingeMagnetAppliesWhenTheDividerIsLetGoNearIt() {
        val hinge = com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds(416, 0, 424, 900)
        val withHinge = window.copy(hinges = listOf(hinge))
        val target = SplitSettle.settleDivider(withHinge, (840f - 405f) / 840f, 0f)
        assertEquals(SplitMode.SPLIT, target.mode)
        assertEquals(0.5f, target.share, 0.01f)
    }

    @Test
    fun aNarrowWindowDividerGoesToAnExtreme() {
        val narrow = window.copy(widthDp = 500f)
        assertEquals(SplitTarget(SplitMode.HOME_ONLY, 0f), SplitSettle.settleDivider(narrow, 0.3f, 0f))
        assertEquals(SplitTarget(SplitMode.STREAM_ONLY, 1f), SplitSettle.settleDivider(narrow, 0.6f, 0f))
    }

    @Test
    fun stepsMoveTheDividerThroughTheStatesAndStopAtTheEnds() {
        val min = SplitGeometry.limits(window).minShare
        val max = SplitGeometry.limits(window).maxShare
        assertEquals(SplitTarget(SplitMode.SPLIT, min), SplitSettle.step(window, SplitMode.HOME_ONLY, 0f, 1))
        assertEquals(SplitTarget(SplitMode.HOME_ONLY, 0f), SplitSettle.step(window, SplitMode.HOME_ONLY, 0f, -1))
        assertEquals(SplitTarget(SplitMode.SPLIT, max), SplitSettle.step(window, SplitMode.STREAM_ONLY, 1f, -1))
        assertEquals(SplitTarget(SplitMode.STREAM_ONLY, 1f), SplitSettle.step(window, SplitMode.STREAM_ONLY, 1f, 1))
        assertEquals(0.45f, SplitSettle.step(window, SplitMode.SPLIT, 0.4f, 1).share, 0.0001f)
        assertEquals(SplitMode.STREAM_ONLY, SplitSettle.step(window, SplitMode.SPLIT, max, 1).mode)
        assertEquals(SplitMode.HOME_ONLY, SplitSettle.step(window, SplitMode.SPLIT, min, -1).mode)
    }

    @Test
    fun revealOpensFromHomeAndClosesFromAMode() {
        assertEquals(0f, SplitReveal.reveal(0f, 0f, opening = true))
        assertEquals(0.6f, SplitReveal.reveal(0.6f, 0f, opening = true), 0.0001f)
        assertEquals(1f, SplitReveal.reveal(0f, 1f, opening = false))
        assertEquals(0.5f, SplitReveal.reveal(0.5f, 1f, opening = false), 0.0001f)
        assertEquals(0f, SplitReveal.reveal(1f, 0.5f, opening = false))
    }

    @Test
    fun progressForInvertsReveal() {
        listOf(true to 0f, false to 1f, false to 0.5f, true to 0.5f).forEach { (opening, start) ->
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { target ->
                val progress = SplitReveal.progressFor(target, start, opening)
                val back = SplitReveal.reveal(progress, start, opening)
                val reachable = if (opening) target >= start else target <= start
                if (reachable) assertEquals(target, back, 0.0001f, "opening=$opening start=$start target=$target")
            }
        }
    }

    @Test
    fun streamShareGrowsFromNothingToTheSplitAndOnToEverything() {
        assertEquals(0f, SplitReveal.streamShare(0f, 0.4f))
        assertEquals(0.4f, SplitReveal.streamShare(0.5f, 0.4f), 0.0001f)
        assertEquals(1f, SplitReveal.streamShare(1f, 0.4f), 0.0001f)
        assertEquals(0.7f, SplitReveal.streamShare(0.75f, 0.4f), 0.0001f)
    }

    @Test
    fun theFrameSlidesInOpeningAndOutClosingAndFadesUnderReducedMotion() {
        val opening = SplitReveal.frame(0.25f, 0.4f, opening = true, reducedMotion = false)
        assertEquals(-0.5f, opening.slideFraction, 0.0001f)
        assertEquals(1f, opening.streamAlpha)
        val closing = SplitReveal.frame(0.25f, 0.4f, opening = false, reducedMotion = false)
        assertEquals(0.5f, closing.slideFraction, 0.0001f)
        val reduced = SplitReveal.frame(0.25f, 0.4f, opening = true, reducedMotion = true)
        assertEquals(0f, reduced.slideFraction)
        assertEquals(0.5f, reduced.streamAlpha, 0.0001f)
        assertEquals(0f, SplitReveal.frame(0.75f, 0.4f, opening = true, reducedMotion = false).slideFraction)
    }

    @Test
    fun revealVelocityFollowsTheDirectionOfTheReveal() {
        assertEquals(2f, SplitReveal.revealVelocity(2f, 0f, opening = true), 0.0001f)
        assertEquals(-1f, SplitReveal.revealVelocity(2f, 0.5f, opening = false), 0.0001f)
    }
}

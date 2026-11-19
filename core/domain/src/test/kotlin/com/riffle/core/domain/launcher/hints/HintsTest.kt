package com.riffle.core.domain.launcher.hints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintsTest {
    private val home =
        HintContext(
            streamAvailable = true,
            libraryAvailable = true,
            dockHasItems = true,
        )

    private val stream = home.copy(streamShown = true, stackCount = 3)

    private fun progress(
        vararg retired: Hint,
        visits: Int = 1,
        elsewhere: Boolean = false,
        lastRetiredVisit: Int? = null,
    ) = HintProgress(retired.toSet(), visits, elsewhere, lastRetiredVisit)

    @Test
    fun aFreshInstallIsOfferedThePressFirst() {
        assertEquals(Hint.DOCK_PRESS, Hints.due(progress(visits = 1), home))
    }

    @Test
    fun atMostOneHintIsDueAtATimeAndTheyComeInOrder() {
        val retiredPress = progress(Hint.DOCK_PRESS, visits = 2, lastRetiredVisit = 1)
        assertEquals(Hint.DOCK_MENU, Hints.due(retiredPress, home))
        val retiredMenu = progress(Hint.DOCK_PRESS, Hint.DOCK_MENU, visits = 3, lastRetiredVisit = 2)
        // The pull waits for its own reason: the Library opened some other way, or enough Home visits.
        assertNull(Hints.due(retiredMenu, home))
    }

    @Test
    fun aHintPutAwayLeavesTheNextHomeHintForALaterVisit() {
        val sameVisit = progress(Hint.DOCK_PRESS, visits = 1, lastRetiredVisit = 1)
        assertNull(Hints.due(sameVisit, home))
        assertEquals(Hint.DOCK_MENU, Hints.due(sameVisit.copy(homeVisits = 2), home))
    }

    @Test
    fun theStackHintIsNotPacedByHomeVisitsBecauseItStandsOverTheStream() {
        val justPressed = progress(Hint.DOCK_PRESS, visits = 1, lastRetiredVisit = 1)
        assertEquals(Hint.DOCK_SCRUB, Hints.due(justPressed, stream))
    }

    @Test
    fun theStackHintNeedsTwoOrMoreStacks() {
        val seen = progress(Hint.DOCK_PRESS)
        assertNull(Hints.due(seen, stream.copy(stackCount = 1)))
        assertEquals(Hint.DOCK_SCRUB, Hints.due(seen, stream.copy(stackCount = 2)))
    }

    @Test
    fun thePullIsHintedAfterTheLibraryWasOpenedAnotherWayOrAfterEnoughVisits() {
        val others = arrayOf(Hint.DOCK_PRESS, Hint.DOCK_MENU)
        assertNull(Hints.due(progress(*others, visits = 3), home))
        assertEquals(Hint.DOCK_PULL, Hints.due(progress(*others, visits = Hints.PULL_AFTER_HOME_VISITS), home))
        assertEquals(Hint.DOCK_PULL, Hints.due(progress(*others, visits = 2, elsewhere = true), home))
    }

    @Test
    fun thePullIsNeverHintedWithoutALibrary() {
        val others = arrayOf(Hint.DOCK_PRESS, Hint.DOCK_MENU)
        assertNull(Hints.due(progress(*others, visits = 9, elsewhere = true), home.copy(libraryAvailable = false)))
    }

    @Test
    fun theMenuHintNeedsAnItemOnTheDock() {
        val seen = progress(Hint.DOCK_PRESS, visits = 2)
        assertNull(Hints.due(seen, home.copy(dockHasItems = false)))
        assertEquals(Hint.DOCK_MENU, Hints.due(seen, home))
    }

    @Test
    fun nothingSpeaksInSimpleModeOverTheSetUpWhileDraggingOrWhileTheDockMoves() {
        assertNull(Hints.due(progress(), home.copy(simpleMode = true)))
        assertNull(Hints.due(progress(), home.copy(setUpShowing = true)))
        assertNull(Hints.due(progress(), home.copy(dragging = true)))
        assertNull(Hints.due(progress(), home.copy(dockBusy = true)))
        assertNull(Hints.due(progress(), stream.copy(setUpShowing = true)))
    }

    @Test
    fun nothingSpeaksOverTheLibraryAndTheHomeHintsLeaveTheStreamAlone() {
        assertNull(Hints.due(progress(), home.copy(libraryShown = true)))
        // Over the Stream the press and menu hints are moot; only the stack hint may stand there.
        assertEquals(Hint.DOCK_SCRUB, Hints.due(progress(), stream))
        assertNull(Hints.due(progress(Hint.DOCK_SCRUB), stream))
    }

    @Test
    fun theStreamOpeningByAnyRouteRetiresThePress() {
        val after = Hints.reduce(progress(), HintEvent.StreamOpened)
        assertTrue(Hint.DOCK_PRESS in after.retired)
        assertEquals(1, after.lastRetiredVisit)
    }

    @Test
    fun theLibraryOpeningByThePullRetiresItsHintAndAnyOtherWayMakesItDue() {
        val pulled = Hints.reduce(progress(), HintEvent.LibraryOpened(byDockPull = true))
        assertTrue(Hint.DOCK_PULL in pulled.retired)
        assertFalse(pulled.libraryOpenedElsewhere)

        val tapped = Hints.reduce(progress(), HintEvent.LibraryOpened(byDockPull = false))
        assertTrue(tapped.libraryOpenedElsewhere)
        assertTrue(Hint.DOCK_PULL !in tapped.retired)
    }

    @Test
    fun performingAGestureOrDismissingRetiresItForGood() {
        assertTrue(Hint.DOCK_SCRUB in Hints.reduce(progress(), HintEvent.StackScrubbed).retired)
        assertTrue(Hint.DOCK_MENU in Hints.reduce(progress(), HintEvent.DockMenuOpened).retired)
        assertTrue(Hint.DOCK_MENU in Hints.reduce(progress(), HintEvent.Dismissed(Hint.DOCK_MENU)).retired)
        // Retiring twice changes nothing, so the visit it was put away on stays the first.
        val once = Hints.reduce(progress(visits = 1), HintEvent.DockMenuOpened)
        assertEquals(once, Hints.reduce(once.copy(homeVisits = 3), HintEvent.DockMenuOpened).copy(homeVisits = 1))
    }

    @Test
    fun homeVisitsCountUp() {
        assertEquals(2, Hints.reduce(progress(visits = 1), HintEvent.HomeVisited).homeVisits)
    }

    @Test
    fun showHintsAgainMakesEveryHintDueAsOnAFreshInstall() {
        val everything = progress(*Hint.entries.toTypedArray(), visits = 9, elsewhere = true, lastRetiredVisit = 9)
        assertEquals(HintProgress(), Hints.reset())
        assertEquals(Hint.DOCK_PRESS, Hints.due(Hints.reset().copy(homeVisits = 1), home))
        assertNull(Hints.due(everything, home))
    }
}

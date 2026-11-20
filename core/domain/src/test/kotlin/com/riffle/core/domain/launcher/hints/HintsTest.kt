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
        val gap = Hints.MIN_VISITS_BETWEEN_HINTS
        val retiredPress = progress(Hint.DOCK_PRESS, visits = 1 + gap, lastRetiredVisit = 1)
        assertEquals(Hint.DOCK_MENU, Hints.due(retiredPress, home))
        val retiredMenu = progress(Hint.DOCK_PRESS, Hint.DOCK_MENU, visits = 1 + 2 * gap, lastRetiredVisit = 1 + gap)
        // The pull waits for its own reason: the Library opened some other way, or enough Home visits.
        assertEquals(Hint.DOCK_PULL, Hints.due(retiredMenu, home))
        assertNull(Hints.due(retiredMenu.copy(homeVisits = Hints.PULL_AFTER_HOME_VISITS - 1), home))
    }

    @Test
    fun aHintShownLeavesTheNextOneForSeveralLaterVisitsNeverTheSameOrTheNext() {
        val gap = Hints.MIN_VISITS_BETWEEN_HINTS
        val shown = progress(Hint.DOCK_PRESS, visits = 1, lastRetiredVisit = 1)
        for (visits in 1 until 1 + gap) {
            assertNull(Hints.due(shown.copy(homeVisits = visits), home), "visit $visits")
        }
        assertEquals(Hint.DOCK_MENU, Hints.due(shown.copy(homeVisits = 1 + gap), home))
    }

    @Test
    fun theStackHintIsPacedLikeTheOthers() {
        val justPressed = progress(Hint.DOCK_PRESS, visits = 1, lastRetiredVisit = 1)
        assertNull(Hints.due(justPressed, stream))
        val later = justPressed.copy(homeVisits = 1 + Hints.MIN_VISITS_BETWEEN_HINTS)
        assertEquals(Hint.DOCK_SCRUB, Hints.due(later, stream))
    }

    @Test
    fun aFirstEverHintIsNotPaced() {
        assertEquals(Hint.DOCK_PRESS, Hints.due(progress(visits = 1, lastRetiredVisit = null), home))
    }

    @Test
    fun aShownHintStaysOnlyWhileItsSituationHolds() {
        assertTrue(Hints.stillApplies(Hint.DOCK_PRESS, home))
        assertFalse(Hints.stillApplies(Hint.DOCK_PRESS, stream))
        assertFalse(Hints.stillApplies(Hint.DOCK_PRESS, home.copy(libraryShown = true)))
        assertFalse(Hints.stillApplies(Hint.DOCK_PRESS, home.copy(dockBusy = true)))
        assertFalse(Hints.stillApplies(Hint.DOCK_PRESS, home.copy(dragging = true)))
        assertFalse(Hints.stillApplies(Hint.DOCK_PRESS, home.copy(simpleMode = true)))
        assertTrue(Hints.stillApplies(Hint.DOCK_SCRUB, stream))
        assertFalse(Hints.stillApplies(Hint.DOCK_SCRUB, home))
        assertFalse(Hints.stillApplies(Hint.DOCK_MENU, home.copy(dockHasItems = false)))
        assertFalse(Hints.stillApplies(Hint.DOCK_PULL, home.copy(libraryAvailable = false)))
    }

    @Test
    fun aHintAlreadyShownIsNeverDueAgainEvenWithoutAGesture() {
        val shown = Hints.reduce(progress(visits = 1), HintEvent.Shown(Hint.DOCK_PRESS))
        assertTrue(Hint.DOCK_PRESS in shown.retired)
        assertEquals(1, shown.lastRetiredVisit)
        for (visits in 1..50) {
            assertTrue(Hints.due(shown.copy(homeVisits = visits), home) != Hint.DOCK_PRESS)
        }
    }

    @Test
    fun aHintForAGesturePerformedIsNeverDue() {
        val pressed = Hints.reduce(progress(visits = 1), HintEvent.StreamOpened)
        assertTrue(Hints.due(pressed.copy(homeVisits = 20), home) != Hint.DOCK_PRESS)
    }

    @Test
    fun theStackHintNeedsTwoOrMoreStacks() {
        val seen = progress(Hint.DOCK_PRESS, visits = 9, lastRetiredVisit = 1)
        assertNull(Hints.due(seen, stream.copy(stackCount = 1)))
        assertEquals(Hint.DOCK_SCRUB, Hints.due(seen, stream.copy(stackCount = 2)))
    }

    @Test
    fun thePullIsHintedAfterTheLibraryWasOpenedAnotherWayOrAfterEnoughVisits() {
        val others = arrayOf(Hint.DOCK_PRESS, Hint.DOCK_MENU)
        assertNull(Hints.due(progress(*others, visits = 3, lastRetiredVisit = 1), home))
        val enough = Hints.PULL_AFTER_HOME_VISITS
        assertEquals(Hint.DOCK_PULL, Hints.due(progress(*others, visits = enough, lastRetiredVisit = 1), home))
        assertEquals(
            Hint.DOCK_PULL,
            Hints.due(progress(*others, visits = 4, elsewhere = true, lastRetiredVisit = 1), home),
        )
    }

    @Test
    fun thePullIsNeverHintedWithoutALibrary() {
        val others = arrayOf(Hint.DOCK_PRESS, Hint.DOCK_MENU)
        assertNull(
            Hints.due(
                progress(*others, visits = 9, elsewhere = true, lastRetiredVisit = 1),
                home.copy(libraryAvailable = false),
            ),
        )
    }

    @Test
    fun theMenuHintNeedsAnItemOnTheDock() {
        val seen = progress(Hint.DOCK_PRESS, visits = 4, lastRetiredVisit = 1)
        val noPull = seen.copy(retired = seen.retired + Hint.DOCK_PULL)
        assertNull(Hints.due(noPull, home.copy(dockHasItems = false)))
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
    fun performingAGestureOrShowingItRetiresItForGood() {
        assertTrue(Hint.DOCK_SCRUB in Hints.reduce(progress(), HintEvent.StackScrubbed).retired)
        assertTrue(Hint.DOCK_MENU in Hints.reduce(progress(), HintEvent.DockMenuOpened).retired)
        assertTrue(Hint.DOCK_MENU in Hints.reduce(progress(), HintEvent.Shown(Hint.DOCK_MENU)).retired)
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

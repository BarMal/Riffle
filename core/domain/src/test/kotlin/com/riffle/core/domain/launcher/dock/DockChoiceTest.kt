package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockChoiceTest {
    private val hold = DockMotionConstants.DEFAULT_HOLD_MILLIS.toLong()
    private val past = DockPress.COMMIT_DP + 4f

    // What is selectable -------------------------------------------------------------------------------------------

    @Test
    fun liveAppsNowPlayingTilesAndPinnedAppsHaveAPageAndAnythingElseDoesNot() {
        assertEquals(StreamFocusTarget.Now, DockChoice.pageFor(DockDynamicSideEntry.NOW_PLAYING_KEY))
        assertEquals(
            StreamFocusTarget.AppStack("chat:p"),
            DockChoice.pageFor(DockDynamicSideEntry.liveAppKey("chat:p")),
        )
        assertEquals(
            StreamFocusTarget.Section(StreamSection.RECENTS),
            DockChoice.pageFor(DockDynamicSideEntry.contextTileKey(StreamSection.RECENTS)),
        )
        // A pinned app opens its live page, else its shortcuts page: the target names the app.
        assertEquals(
            StreamFocusTarget.AppShortcuts("mail:personal"),
            DockChoice.pageFor(DockDynamicSideEntry.pinnedAppKey("mail:personal")),
        )
        assertTrue(DockChoice.isSelectable(DockDynamicSideEntry.pinnedAppKey("mail:personal")))
        // An unknown section and anything else (a dock item's id, a folder) have no page.
        assertNull(DockChoice.pageFor("item-123"))
        assertNull(DockChoice.pageFor("ctx:nothing"))
        assertFalse(DockChoice.isSelectable("item-123"))
        assertTrue(DockChoice.isSelectable("now"))
    }

    @Test
    fun aPinnedAppKeyIsTheAppKeyUnderThePrefixAndRoundTripsThroughThePage() {
        assertEquals("pin:mail:personal", DockDynamicSideEntry.pinnedAppKey("mail:personal"))
        assertEquals(
            StreamFocusTarget.AppShortcuts("a.b.c:work"),
            DockChoice.pageFor(DockDynamicSideEntry.pinnedAppKey("a.b.c:work")),
        )
    }

    @Test
    fun withPinnedAppsSelectableTheNearestItemMayBeOne() {
        val row =
            listOf(
                DockChoiceItem(DockDynamicSideEntry.pinnedAppKey("a:personal"), 0f, 48f),
                DockChoiceItem(DockDynamicSideEntry.pinnedAppKey("b:personal"), 56f, 104f),
                DockChoiceItem("live:chat:p", 120f, 168f),
                DockChoiceItem("folder-1", 176f, 224f),
            )
        assertEquals("pin:a:personal", DockChoice.nearestSelectable(20f, row))
        assertEquals("pin:b:personal", DockChoice.nearestSelectable(90f, row))
        assertEquals("live:chat:p", DockChoice.nearestSelectable(150f, row))
        // A folder in the Dock has no page, so the nearest item with one wins.
        assertEquals("live:chat:p", DockChoice.nearestSelectable(210f, row))
    }

    @Test
    fun aPressThatEndsOnThePinnedAppItStartedOnClosesAndOnAnotherPinnedAppSwitches() {
        val a = DockDynamicSideEntry.pinnedAppKey("a:personal")
        val b = DockDynamicSideEntry.pinnedAppKey("b:personal")
        // Open Stream, a thumb that stayed on the pinned app: a plain press.
        assertEquals(
            DockPressOutcome.Close,
            DockChoice.outcome(
                true,
                true,
                true,
                selectedKey = a,
                cancelling = false,
                startedOnEntryKey = a,
                startedNearKey = a,
            ),
        )
        // Slid onto the next pinned app: its shortcuts page.
        assertEquals(
            DockPressOutcome.SwitchPage(b),
            DockChoice.outcome(
                true,
                true,
                true,
                selectedKey = b,
                cancelling = false,
                startedOnEntryKey = a,
                startedNearKey = a,
            ),
        )
        // From Home, a press that starts on a pinned app opens its page.
        assertEquals(
            DockPressOutcome.OpenPage(a),
            DockChoice.outcome(false, true, false, selectedKey = null, cancelling = false, startedOnEntryKey = a),
        )
    }

    // The nearest selectable item ----------------------------------------------------------------------------------

    private val items =
        listOf(
            DockChoiceItem("pin-a", 0f, 56f),
            DockChoiceItem("pin-b", 66f, 122f),
            DockChoiceItem("live:chat:p", 140f, 188f),
            DockChoiceItem("now", 198f, 246f),
            DockChoiceItem("ctx:recents", 270f, 318f),
        )

    @Test
    fun theNearestSelectableItemIsTheOneUnderTheFingerAndPinnedAppsAreSkipped() {
        assertEquals("live:chat:p", DockChoice.nearestSelectable(150f, items))
        assertEquals("now", DockChoice.nearestSelectable(230f, items))
        assertEquals("ctx:recents", DockChoice.nearestSelectable(500f, items))
        // Over a pinned app, it snaps to the nearest item that has a page; never the pinned app itself.
        assertEquals("live:chat:p", DockChoice.nearestSelectable(30f, items))
        assertEquals("live:chat:p", DockChoice.nearestSelectable(100f, items))
        assertEquals("live:chat:p", DockChoice.nearestSelectable(-40f, items))
    }

    @Test
    fun inAGapTheCloserCentreWinsAndATieGoesToTheEarlierItem() {
        // Centres 164 and 222: the gap between them is nearer the first below 193 and the second above it.
        assertEquals("live:chat:p", DockChoice.nearestSelectable(192f, items))
        assertEquals("now", DockChoice.nearestSelectable(194f, items))
        val tied = listOf(DockChoiceItem("now", 0f, 10f), DockChoiceItem("ctx:agenda", 20f, 30f))
        assertEquals("now", DockChoice.nearestSelectable(10f, tied))
    }

    @Test
    fun withNothingSelectableThereIsNoSelection() {
        assertNull(DockChoice.nearestSelectable(10f, items.take(2)))
        assertNull(DockChoice.nearestSelectable(10f, emptyList()))
    }

    // Edge scrolling -----------------------------------------------------------------------------------------------

    @Test
    fun holdingNearEitherEndScrollsThatWayAndTheMiddleDoesNot() {
        // A 300-long viewport from 100 to 400: end zones of 45.
        assertEquals(-1, DockChoice.edgeDirection(120f, 100f, 400f))
        assertEquals(-1, DockChoice.edgeDirection(60f, 100f, 400f))
        assertEquals(1, DockChoice.edgeDirection(380f, 100f, 400f))
        assertEquals(1, DockChoice.edgeDirection(900f, 100f, 400f))
        assertEquals(0, DockChoice.edgeDirection(145f, 100f, 400f))
        assertEquals(0, DockChoice.edgeDirection(250f, 100f, 400f))
        assertEquals(0, DockChoice.edgeDirection(355f, 100f, 400f))
        assertEquals(0, DockChoice.edgeDirection(120f, 100f, 100f))
    }

    @Test
    fun theEdgeScrollIsOneSpeedInDpPerSecondWhateverTheFrameRate() {
        val perSecond = DockMotionConstants.CHOOSE_EDGE_SCROLL_DP_PER_SECOND
        assertEquals(perSecond / 60f, DockChoice.edgeScrollDp(1, 1f / 60f), 1e-3f)
        assertEquals(-perSecond / 60f, DockChoice.edgeScrollDp(-1, 1f / 60f), 1e-3f)
        assertEquals(0f, DockChoice.edgeScrollDp(0, 1f / 60f))
        // A stalled frame does not leap.
        assertEquals(perSecond * DockMotionConstants.MAX_FRAME_SECONDS, DockChoice.edgeScrollDp(1, 2f), 1e-3f)
    }

    // Cancel and haptics -------------------------------------------------------------------------------------------

    @Test
    fun theFingerCancelsOnlyBeyondOneDockThicknessAboveTheDock() {
        assertFalse(DockChoice.isCancelling(awayDp = -20f, dockThicknessDp = 80f))
        assertFalse(DockChoice.isCancelling(awayDp = 0f, dockThicknessDp = 80f))
        assertFalse(DockChoice.isCancelling(awayDp = 80f, dockThicknessDp = 80f))
        assertTrue(DockChoice.isCancelling(awayDp = 81f, dockThicknessDp = 80f))
        assertFalse(DockChoice.isCancelling(awayDp = 500f, dockThicknessDp = 0f))
    }

    @Test
    fun theTickSoundsOnlyWhenTheSelectionLandsOnAnotherItem() {
        assertTrue(DockChoice.shouldTick(null, "now"))
        assertTrue(DockChoice.shouldTick("now", "ctx:recents"))
        assertFalse(DockChoice.shouldTick("now", "now"))
        assertFalse(DockChoice.shouldTick("now", null))
        assertFalse(DockChoice.shouldTick(null, null))
    }

    // When choosing starts -----------------------------------------------------------------------------------------

    private fun DockChoiceStart.at(
        time: Long,
        travel: Float = past,
        along: Float = 0f,
    ) = next(time, travel, along, hold)

    @Test
    fun heldStillPastTheThresholdForTheHoldTimeStartsChoosing() {
        var state = DockChoiceStart().at(1_000)
        assertFalse(state.choosing)
        assertEquals(1_000 + hold, state.deadlineMillis(hold))
        state = state.at(1_000 + hold - 1)
        assertFalse(state.choosing)
        state = state.at(1_000 + hold)
        assertTrue(state.choosing)
        assertNull(state.deadlineMillis(hold))
    }

    @Test
    fun theHoldTimeIsTheTuningsAndNotAHardcodedNumber() {
        assertEquals(220, DockMotionTuning().holdMillis)
        val state = DockChoiceStart().next(0, past, 0f, holdMillis = 500)
        assertFalse(state.next(499, past, 0f, 500).choosing)
        assertTrue(state.next(500, past, 0f, 500).choosing)
        assertEquals(500L, state.deadlineMillis(500))
    }

    @Test
    fun nothingStartsBeforeTheThresholdHoweverLongOrFarItGoes() {
        val shortOf = DockPress.COMMIT_DP - 1f
        var state = DockChoiceStart().at(0, travel = shortOf)
        assertNull(state.deadlineMillis(hold))
        state = state.at(10_000, travel = shortOf)
        assertFalse(state.choosing)
        // Sliding a long way sideways short of the threshold is no choice either.
        assertFalse(DockChoiceStart().at(0, travel = shortOf, along = 200f).choosing)
    }

    @Test
    fun slidingSidewaysPastTheThresholdStartsChoosingAtOnceAndOnlyBeyondTheSlideDistance() {
        val slide = DockMotionConstants.CHOOSE_SLIDE_DP
        assertFalse(DockChoiceStart().at(0, along = slide).choosing)
        assertTrue(DockChoiceStart().at(0, along = slide + 1f).choosing)
        assertTrue(DockChoiceStart().at(0, along = -(slide + 1f)).choosing)
    }

    @Test
    fun movingTheFingerRestartsTheHoldButJitterDoesNot() {
        var state = DockChoiceStart().at(0)
        // A little jitter keeps the hold running.
        state = state.at(100, travel = past + 2f, along = 3f)
        assertEquals(0L, state.restingSinceMillis)
        state = state.at(hold)
        assertTrue(state.choosing)

        // A real move (pressing deeper) begins the hold again from there.
        var moving = DockChoiceStart().at(0)
        moving = moving.at(150, travel = past + 20f)
        assertEquals(150L, moving.restingSinceMillis)
        assertFalse(moving.at(150 + hold - 1, travel = past + 20f).choosing)
        assertTrue(moving.at(150 + hold, travel = past + 20f).choosing)
    }

    @Test
    fun drawingThePressBackAboveTheThresholdBeforeChoosingStartsCancelsTheHold() {
        var state = DockChoiceStart().at(0)
        state = state.at(100, travel = DockPress.COMMIT_DP - 2f)
        assertNull(state.restingSinceMillis)
        assertNull(state.deadlineMillis(hold))
        // Pressing past again starts a fresh hold.
        state = state.at(300)
        assertEquals(300L, state.restingSinceMillis)
        assertFalse(state.at(300 + hold - 1).choosing)
    }

    @Test
    fun onceChoosingStartedItStaysStartedWhateverTheFingerDoes() {
        var state = DockChoiceStart().at(0, along = 40f)
        assertTrue(state.choosing)
        state = state.at(50, travel = 0f)
        assertTrue(state.choosing)
    }

    @Test
    fun withTheStreamOpenHoldingStillNeverStartsChoosingHoweverLong() {
        var state = DockChoiceStart().next(0, past, 0f, hold, holdStarts = false)
        assertNull(state.restingSinceMillis)
        assertNull(state.deadlineMillis(hold))
        state = state.next(hold, past, 0f, hold, holdStarts = false)
        state = state.next(10_000, past + 1f, 2f, hold, holdStarts = false)
        assertFalse(state.choosing)
        assertNull(state.deadlineMillis(hold))
    }

    @Test
    fun withTheStreamOpenSlidingAlongTheRunStillChoosesAtTheSlideDistanceAndOnlyBeyondIt() {
        val slide = DockMotionConstants.CHOOSE_SLIDE_FROM_OPEN_DP
        assertTrue(slide > DockMotionConstants.CHOOSE_SLIDE_DP)
        // A thumb's drift (the Home slide distance and a good deal more) is not a choice with the Stream open.
        assertFalse(DockChoiceStart().next(0, past, DockMotionConstants.CHOOSE_SLIDE_DP + 12f, hold, false).choosing)
        assertFalse(DockChoiceStart().next(0, past, slide, hold, holdStarts = false).choosing)
        assertTrue(DockChoiceStart().next(0, past, slide + 1f, hold, holdStarts = false).choosing)
        assertTrue(DockChoiceStart().next(0, past, -(slide + 1f), hold, holdStarts = false).choosing)
        // Never before the threshold, here either.
        val shortOf = DockPress.COMMIT_DP - 1f
        assertFalse(DockChoiceStart().next(0, shortOf, 200f, hold, holdStarts = false).choosing)
    }

    @Test
    fun holdingStillChoosesFromHomeByDefault() {
        val state = DockChoiceStart().next(0, past, 0f, hold).next(hold, past, 0f, hold)
        assertTrue(state.choosing)
    }

    // What a release does ------------------------------------------------------------------------------------------

    private fun outcome(
        fromContextual: Boolean = false,
        committed: Boolean = true,
        choosing: Boolean = false,
        selectedKey: String? = null,
        cancelling: Boolean = false,
        startedOn: String? = null,
        startedNear: String? = null,
    ) = DockChoice.outcome(fromContextual, committed, choosing, selectedKey, cancelling, startedOn, startedNear)

    @Test
    fun aQuickPressFromHomeOpensTheLastPageOrTheOneItStartedOn() {
        assertEquals(DockPressOutcome.OpenPage(null), outcome())
        assertEquals(DockPressOutcome.OpenPage("now"), outcome(startedOn = "now"))
        assertEquals(DockPressOutcome.Revert, outcome(committed = false, startedOn = "now"))
    }

    @Test
    fun aQuickPressInTheContextualModeClosesAndAShortOneDoesNothing() {
        assertEquals(DockPressOutcome.Close, outcome(fromContextual = true))
        assertEquals(DockPressOutcome.Close, outcome(fromContextual = true, startedOn = "now"))
        assertEquals(DockPressOutcome.Revert, outcome(fromContextual = true, committed = false))
    }

    @Test
    fun choosingFromHomeOpensTheSelectedPageWhateverTheTravelAtTheLift() {
        assertEquals(DockPressOutcome.OpenPage("ctx:agenda"), outcome(choosing = true, selectedKey = "ctx:agenda"))
        assertEquals(
            DockPressOutcome.OpenPage("ctx:agenda"),
            outcome(choosing = true, committed = false, selectedKey = "ctx:agenda", startedOn = "now"),
        )
    }

    @Test
    fun choosingInTheContextualModeSwitchesPagesWithoutClosing() {
        assertEquals(
            DockPressOutcome.SwitchPage("ctx:agenda"),
            outcome(fromContextual = true, choosing = true, selectedKey = "ctx:agenda"),
        )
        assertEquals(
            DockPressOutcome.SwitchPage("live:chat:p"),
            outcome(fromContextual = true, choosing = true, committed = false, selectedKey = "live:chat:p"),
        )
    }

    @Test
    fun aSelectionThatEndsOnTheItemThePressStartedNearClosesTheOpenStreamLikeAPlainPress() {
        assertEquals(
            DockPressOutcome.Close,
            outcome(
                fromContextual = true,
                choosing = true,
                selectedKey = "ctx:agenda",
                startedOn = "ctx:agenda",
                startedNear = "ctx:agenda",
            ),
        )
        // The press started on a pinned app (no key of its own): what is nearest it counts.
        assertEquals(
            DockPressOutcome.Close,
            outcome(
                fromContextual = true,
                choosing = true,
                committed = false,
                selectedKey = "live:chat:p",
                startedNear = "live:chat:p",
            ),
        )
    }

    @Test
    fun aSelectionThatMovedToAnotherItemStillSwitchesPagesFromTheOpenStream() {
        assertEquals(
            DockPressOutcome.SwitchPage("ctx:tasks"),
            outcome(fromContextual = true, choosing = true, selectedKey = "ctx:tasks", startedNear = "ctx:agenda"),
        )
    }

    @Test
    fun startingNearTheSelectedItemMakesNoDifferenceFromHome() {
        assertEquals(
            DockPressOutcome.OpenPage("ctx:agenda"),
            outcome(choosing = true, selectedKey = "ctx:agenda", startedNear = "ctx:agenda"),
        )
    }

    @Test
    fun withNothingSelectableHomeFallsBackToAPlainPressAndTheOpenStreamChangesNothing() {
        assertEquals(DockPressOutcome.OpenPage(null), outcome(choosing = true))
        assertEquals(DockPressOutcome.OpenPage("now"), outcome(choosing = true, startedOn = "now"))
        // Nothing was chosen in the open Stream: a press past the threshold closes it as a plain press does.
        assertEquals(DockPressOutcome.Close, outcome(fromContextual = true, choosing = true))
        assertEquals(DockPressOutcome.Revert, outcome(fromContextual = true, choosing = true, committed = false))
    }

    @Test
    fun draggingWellAwayWhileChoosingCancelsFromEitherMode() {
        assertEquals(
            DockPressOutcome.Revert,
            outcome(choosing = true, selectedKey = "ctx:agenda", cancelling = true),
        )
        assertEquals(
            DockPressOutcome.Revert,
            outcome(fromContextual = true, choosing = true, selectedKey = "ctx:agenda", cancelling = true),
        )
    }
}

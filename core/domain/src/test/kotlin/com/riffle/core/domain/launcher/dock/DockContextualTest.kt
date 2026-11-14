package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamPageRef
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.ref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockContextualTest {
    private fun page(
        section: StreamSection,
        groupKey: String? = null,
    ) = StreamPage(
        key = if (groupKey == null) section.storedId else "${section.storedId}/$groupKey",
        section = section,
        groupKey = groupKey,
        label = null,
        count = 0,
        view = StreamView.STACK,
        body = StreamPageBody.State(StreamSectionResult.Empty),
    )

    // The planner's order for the default layout: now, notifications (three apps), recents, agenda, tasks, feeds (two).
    private val planned =
        listOf(
            page(StreamSection.NOW),
            page(StreamSection.NOTIFICATIONS, "chat:p"),
            page(StreamSection.NOTIFICATIONS, "mail:p"),
            page(StreamSection.NOTIFICATIONS, "maps:p"),
            page(StreamSection.RECENTS),
            page(StreamSection.AGENDA),
            page(StreamSection.TASKS),
            page(StreamSection.FEEDS, "f1"),
            page(StreamSection.FEEDS, "f2"),
        )

    private fun keys(pages: List<StreamPage>) = pages.map { it.key }

    @Test
    fun pagesFollowTheDockLiveAppsFirstThenTheBuiltInPagesInLayoutOrder() {
        val ordered = DockPageOrder.order(planned, liveGroupKeys = listOf("mail:p", "chat:p"), nowPlaying = false)
        assertEquals(
            listOf(
                "notifications/mail:p",
                "notifications/chat:p",
                "notifications/maps:p",
                "now",
                "recents",
                "agenda",
                "tasks",
                "feeds/f1",
                "feeds/f2",
            ),
            keys(ordered),
        )
    }

    @Test
    fun aPlayingNowPageSitsRightAfterTheLiveAppsLikeItsDockEntry() {
        val layout = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.Move(StreamSection.NOW, 5))
        val moved = planned.filter { it.section != StreamSection.NOW } + page(StreamSection.NOW)
        assertEquals(StreamSection.NOW, layout.sections.last().section)
        val ordered = DockPageOrder.order(moved, listOf("chat:p"), nowPlaying = true)
        assertEquals(
            listOf("notifications/chat:p", "notifications/mail:p", "notifications/maps:p", "now", "recents"),
            keys(ordered).take(5),
        )
    }

    @Test
    fun theOrderIsAPermutationNothingIsLostOrRepeated() {
        val ordered = DockPageOrder.order(planned, listOf("maps:p", "unknown:p"), nowPlaying = true)
        assertEquals(keys(planned).sorted(), keys(ordered).sorted())
    }

    @Test
    fun withNoLiveAppsThePagesKeepTheLayoutOrder() {
        val noApps = planned.filterNot { it.ref().isAppPage }
        assertEquals(keys(noApps), keys(DockPageOrder.order(noApps, emptyList(), nowPlaying = false)))
    }

    @Test
    fun pagesWalkTheDockLeftToRight() {
        // The Dock's entries (live apps, now playing, tiles) and the pager's pages are the same sequence.
        val live = listOf("mail:p", "chat:p")
        val ordered = DockPageOrder.order(planned, live, nowPlaying = true)
        val refs = ordered.map(StreamPage::ref)
        val entries =
            DockDynamicSide.entries(
                emptyList(),
                null,
                0,
                DockDynamicSide.contextSections(refs, nowPlaying = false),
            )
        val tileSections = entries.map { (it as DockDynamicSideEntry.ContextTile).section }
        val firstOfEach = refs.filterNot { it.isAppPage }.map { it.section }.distinct()
        assertEquals(firstOfEach, tileSections)
    }

    private val entryKeys = listOf("live:chat:p", "live:mail:p", "now", "ctx:agenda", "ctx:feeds")

    @Test
    fun theOpenAppPageRingsItsLiveAppIcon() {
        assertEquals(
            "live:mail:p",
            DockRing.entryKeyFor(
                StreamPageRef("notifications/mail:p", StreamSection.NOTIFICATIONS, "mail:p"),
                entryKeys,
            ),
        )
    }

    @Test
    fun aLiveAppPastTheDockSlotsRingsNothing() {
        assertNull(
            DockRing.entryKeyFor(StreamPageRef("notifications/x:p", StreamSection.NOTIFICATIONS, "x:p"), entryKeys),
        )
    }

    @Test
    fun theNowPageRingsNowPlayingWhileItIsShownElseItsTile() {
        val now = StreamPageRef("now", StreamSection.NOW, null)
        assertEquals("now", DockRing.entryKeyFor(now, entryKeys))
        assertEquals("ctx:now", DockRing.entryKeyFor(now, listOf("ctx:now", "ctx:agenda")))
    }

    @Test
    fun anyPageOfASectionRingsThatSectionsTile() {
        val second = StreamPageRef("feeds/f2", StreamSection.FEEDS, "f2")
        assertEquals("ctx:feeds", DockRing.entryKeyFor(second, entryKeys))
        assertEquals("ctx:agenda", DockRing.entryKeyFor(StreamPageRef("agenda", StreamSection.AGENDA, null), entryKeys))
    }

    @Test
    fun aSectionWithoutATileOrNoOpenPageRingsNothing() {
        assertNull(DockRing.entryKeyFor(StreamPageRef("tasks", StreamSection.TASKS, null), entryKeys))
        assertNull(DockRing.entryKeyFor(null, entryKeys))
    }

    private val tiles = listOf(StreamSection.NOW, StreamSection.RECENTS, StreamSection.AGENDA, StreamSection.TASKS)

    @Test
    fun movingATileEarlierOrLaterIsAOneStepLayoutMoveOverSectionsThatDrawNoTile() {
        // Layout order: now, notifications, recents, agenda, tasks, feeds. Recents earlier jumps over notifications.
        assertEquals(-2, DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.RECENTS, -1))
        assertEquals(1, DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.RECENTS, 1))
    }

    @Test
    fun aTileAtEitherEndCannotMovePastIt() {
        assertNull(DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.NOW, -1))
        assertNull(DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.TASKS, 1))
        assertNull(DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.FEEDS, 1))
    }

    @Test
    fun theMoveTheDeltaDescribesSwapsTheTilesAsTheDockShowsThem() {
        val delta = requireNotNull(DockTileMoves.deltaFor(StreamLayout.DEFAULT, tiles, StreamSection.RECENTS, -1))
        val moved =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.Move(StreamSection.RECENTS, delta),
            )
        val order = moved.sections.map { it.section }.filter { it in tiles }
        assertEquals(listOf(StreamSection.RECENTS, StreamSection.NOW, StreamSection.AGENDA, StreamSection.TASKS), order)
    }

    @Test
    fun anItemAlreadyInViewDoesNotScroll() {
        assertEquals(
            40,
            DockRunScroll.toReveal(
                scrollPx = 40,
                viewportPx = 300,
                maxScrollPx = 500,
                itemStartPx = 100,
                itemEndPx = 148,
            ),
        )
    }

    @Test
    fun anItemPastTheEndScrollsJustEnoughToShowItAndOneBeforeTheStartScrollsBack() {
        assertEquals(
            448 - 300,
            DockRunScroll.toReveal(
                scrollPx = 0,
                viewportPx = 300,
                maxScrollPx = 500,
                itemStartPx = 400,
                itemEndPx = 448,
            ),
        )
        assertEquals(
            100,
            DockRunScroll.toReveal(
                scrollPx = 300,
                viewportPx = 300,
                maxScrollPx = 500,
                itemStartPx = 100,
                itemEndPx = 148,
            ),
        )
    }

    @Test
    fun aMarginKeepsTheNeighbourPeekingAndNeverOutsideTheRow() {
        assertEquals(
            448 + 24 - 300,
            DockRunScroll.toReveal(0, 300, 500, itemStartPx = 400, itemEndPx = 448, marginPx = 24),
        )
        // The last item of the row: the margin cannot scroll past the end.
        assertEquals(
            500,
            DockRunScroll.toReveal(0, 300, 500, itemStartPx = 752, itemEndPx = 800, marginPx = 24),
        )
        assertEquals(0, DockRunScroll.toReveal(90, 300, 500, itemStartPx = 0, itemEndPx = 48, marginPx = 24))
    }

    @Test
    fun aRowThatDoesNotScrollStaysAtZero() {
        assertEquals(0, DockRunScroll.toReveal(0, 300, 0, itemStartPx = 100, itemEndPx = 148))
    }

    @Test
    fun theContextualRunLaysTheLiveEntriesThenADividerThenTheTiles() {
        // 100 of pins, then a 17 divider; three 48 entries 10 apart (two live, one tile) with a divider between groups.
        val run =
            DockRunGeometry.contextual(
                100,
                liveCount = 2,
                tileCount = 1,
                entryDp = 48,
                spacingDp = 10,
                dividerDp = 17,
            )
        assertEquals(
            listOf(
                DockRunGeometry.Extent(117, 165),
                DockRunGeometry.Extent(175, 223),
                DockRunGeometry.Extent(223 + 10 + 17 + 10, 223 + 10 + 17 + 10 + 48),
            ),
            run.entries,
        )
        assertEquals(run.entries.last().endDp, run.contentDp)
    }

    @Test
    fun withNoLiveEntriesTheTilesFollowTheDividerAtOnce() {
        val run =
            DockRunGeometry.contextual(
                100,
                liveCount = 0,
                tileCount = 2,
                entryDp = 48,
                spacingDp = 10,
                dividerDp = 17,
            )
        assertEquals(listOf(DockRunGeometry.Extent(117, 165), DockRunGeometry.Extent(175, 223)), run.entries)
    }

    @Test
    fun aRunWithNoEntriesIsJustThePinnedBlock() {
        val run = DockRunGeometry.contextual(100, 0, 0, 48, 10, 17)
        assertEquals(DockRunGeometry.Run(emptyList(), 100), run)
    }

    @Test
    fun theRunKnowsWhereEachPinnedItemIs() {
        // 3 pins of 48 with 10 between them = 164.
        val run = DockRunGeometry.contextual(164, 1, 1, 48, 10, 17, staticCount = 3)
        assertEquals(
            listOf(DockRunGeometry.Extent(0, 48), DockRunGeometry.Extent(58, 106), DockRunGeometry.Extent(116, 164)),
            run.pinned,
        )
        assertEquals(5, run.items.size)
        assertEquals(listOf(0, 58, 116, 181, 266), run.stopsDp)
    }

    @Test
    fun theSpacingClosesUpUntilSixWholeItemsFitButNeverBelowTheFloorOrAboveThePreference() {
        // 332 dp of viewport (a 360 dp phone): 6 * 48 = 288, 44 left for 5 gaps -> 8.
        assertEquals(8, DockRunGeometry.contextualSpacingDp(332, 10))
        // Plenty of room: the Dock's own gap.
        assertEquals(10, DockRunGeometry.contextualSpacingDp(532, 10))
        // Too narrow for six even at the floor: the floor.
        assertEquals(4, DockRunGeometry.contextualSpacingDp(250, 10))
        // A Dock whose own gap is smaller than the floor keeps its own.
        assertEquals(2, DockRunGeometry.contextualSpacingDp(250, 2))
        assertEquals(0, DockRunGeometry.contextualSpacingDp(0, 0))
    }

    @Test
    fun sixWholeItemsFitA360DpPhonesViewportWhateverTheDocksOwnGap() {
        (0..24).forEach { gap ->
            val spacing = DockRunGeometry.contextualSpacingDp(332, gap)
            val six = DockRunGeometry.MIN_VISIBLE_ITEMS * DockRunGeometry.CONTEXTUAL_ITEM_DP + 5 * spacing
            assertTrue(six <= 332 || spacing == minOf(4, gap), "gap $gap -> $spacing: $six")
        }
    }

    @Test
    fun aRowThatOverflowsEndsOnAnItemStartSoTheLastScrollLeavesNoSliver() {
        // 6 pins + 5 entries at 48/8: content well past a 332 viewport.
        val run =
            DockRunGeometry.contextual(
                staticDp = 6 * 48 + 5 * 8,
                liveCount = 2,
                tileCount = 3,
                entryDp = 48,
                spacingDp = 8,
                dividerDp = 17,
                staticCount = 6,
                viewportDp = 332,
            )
        val maxScroll = run.contentDp - 332
        assertTrue(maxScroll in run.stopsDp, "max scroll $maxScroll is a stop of ${run.stopsDp}")
        assertTrue(run.endPaddingDp in 0 until 48 + 17 + 8)
    }

    @Test
    fun aRowThatFitsHasNoPaddingAndNoScroll() {
        val run = DockRunGeometry.contextual(100, 1, 1, 48, 10, 17, staticCount = 2, viewportDp = 500)
        assertEquals(0, run.endPaddingDp)
    }

    private val wholeItems = (0 until 12).map { DockRunGeometry.Extent(it * 56, it * 56 + 48) }
    private val wholeStops = wholeItems.map { it.startDp } + listOf(12 * 56)

    @Test
    fun anItemAlreadyWhollyInViewIsLeftAlone() {
        assertEquals(
            112,
            DockRunScroll.toRevealWhole(112, 332, 400, wholeStops, wholeItems, itemIndex = 4),
        )
    }

    @Test
    fun scrollingForwardStopsWithAWholeItemAtTheLeadingEdgeAndTheNextOneShowing() {
        val target = DockRunScroll.toRevealWhole(0, 332, 400, wholeStops, wholeItems, itemIndex = 8)
        assertTrue(target in wholeStops, "$target is a stop")
        // The item (448..496) and the one after it (504..552) are both whole in view.
        assertTrue(target + 332 >= 552)
        assertTrue(target <= 448)
    }

    @Test
    fun scrollingBackStopsAtTheItemBeforeSoTheRowReadsAsOneThatGoesOn() {
        val target = DockRunScroll.toRevealWhole(448, 332, 400, wholeStops, wholeItems, itemIndex = 2)
        assertEquals(56, target)
    }

    @Test
    fun aStopPastTheRowsEndIsNeverChosenAndTheFirstItemGoesToZero() {
        val target = DockRunScroll.toRevealWhole(0, 332, 336, wholeStops, wholeItems, itemIndex = 11)
        assertTrue(target in wholeStops && target <= 336)
        assertEquals(0, DockRunScroll.toRevealWhole(200, 332, 336, wholeStops, wholeItems, itemIndex = 0))
    }

    @Test
    fun noItemMeansNoMove() {
        assertEquals(40, DockRunScroll.toRevealWhole(40, 300, 100, wholeStops, wholeItems, itemIndex = -1))
        assertEquals(40, DockRunScroll.toRevealWhole(40, 300, 100, wholeStops, wholeItems, itemIndex = 99))
    }

    @Test
    fun theRevealUsesTheDensityToTurnDpIntoPixels() {
        val stopsPx = wholeStops.map { it * 2 }
        val target = DockRunScroll.toRevealWhole(0, 664, 800, stopsPx, wholeItems, itemIndex = 9, pxPerDp = 2f)
        assertTrue(target in stopsPx)
        assertTrue(target + 664 >= 9 * 56 * 2 + 96)
    }
}

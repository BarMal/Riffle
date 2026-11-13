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
}

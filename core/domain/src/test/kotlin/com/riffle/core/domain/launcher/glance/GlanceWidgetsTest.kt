package com.riffle.core.domain.launcher.glance

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WidgetEditResult
import com.riffle.core.domain.launcher.home.WidgetEngine
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.stream.ArticleAction
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventAction
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlanceWidgetsTest {
    private val engine = WidgetEngine()

    @Test
    fun theCatalogueIsTheThreeFixedWidgetsWithStableStoredNames() {
        assertEquals(
            listOf("glance.NowPlaying", "glance.NextEvent", "glance.TopStory"),
            GlanceWidgets.catalogue.map { it.storedClass },
        )
        assertEquals(
            listOf("now-playing", "next-event", "top-story"),
            GlanceWidgets.catalogue.map { it.storedId },
        )
        assertEquals(
            listOf(StreamSection.NOW, StreamSection.TODAY, StreamSection.FEEDS),
            GlanceWidgets.catalogue.map { it.section },
        )
    }

    @Test
    fun spansAreOrderedAndFitAPhoneGrid() {
        GlanceWidgets.catalogue.forEach { kind ->
            assertTrue(kind.minSpan.columns <= kind.defaultSpan.columns && kind.minSpan.rows <= kind.defaultSpan.rows)
            assertTrue(kind.defaultSpan.columns <= kind.maxSpan.columns && kind.defaultSpan.rows <= kind.maxSpan.rows)
            assertTrue(kind.resizeConstraints.permits(kind.minSpan))
            assertTrue(kind.resizeConstraints.permits(kind.defaultSpan))
            assertTrue(kind.resizeConstraints.permits(kind.maxSpan))
            assertTrue(!kind.resizeConstraints.permits(GridSpan(kind.maxSpan.columns + 1, kind.maxSpan.rows)))
            assertTrue(kind.defaultSpan.columns <= 4, "a default span fits the narrowest grid, four columns")
            assertTrue(kind.fullMinSpan.columns <= kind.maxSpan.columns && kind.fullMinSpan.rows <= kind.maxSpan.rows)
        }
    }

    @Test
    fun theLayoutFollowsTheSpan() {
        val story = GlanceWidgetKind.TOP_STORY
        assertEquals(GlanceLayout.MINIMAL, story.layoutFor(story.minSpan))
        assertEquals(GlanceLayout.COMPACT, story.layoutFor(story.defaultSpan))
        assertEquals(GlanceLayout.FULL, story.layoutFor(story.maxSpan))
        assertEquals(GlanceLayout.FULL, GlanceWidgetKind.NEXT_EVENT.layoutFor(GlanceWidgetKind.NEXT_EVENT.defaultSpan))
        assertEquals(GlanceLayout.MINIMAL, GlanceWidgetKind.NOW_PLAYING.layoutFor(GridSpan(2, 4)))
        assertTrue(!GlanceLayout.MINIMAL.showsFullTransport)
        assertTrue(GlanceLayout.COMPACT.showsFullTransport && GlanceLayout.FULL.showsFullTransport)
    }

    @Test
    fun theReservedIdentityIsRecognisedOnlyForRiffleItself() {
        GlanceWidgets.catalogue.forEach { assertEquals(it, GlanceWidgets.kindOf(GlanceWidgets.identity(it))) }
        val platform = WidgetProviderIdentity(AppPackageName("com.example.weather"), WidgetProviderClassName("W"))
        assertNull(GlanceWidgets.kindOf(platform))
        assertNull(GlanceWidgets.kindOf(null))
        // A glance class a newer build adds is an ordinary (unbound) widget here, never a crash.
        val future =
            WidgetProviderIdentity(
                AppPackageName(GlanceWidgets.PROVIDER_PACKAGE),
                WidgetProviderClassName("glance.Tasks"),
            )
        assertNull(GlanceWidgets.kindOf(future))
        assertEquals(GlanceWidgetKind.TOP_STORY, GlanceWidgets.kindOfStoredId("top-story"))
        assertNull(GlanceWidgets.kindOfStoredId("nope"))
    }

    @Test
    fun addingPlacesInTheFirstFreeCellsAtTheDefaultSpanWithNoHostId() {
        val updated =
            assertIs<WidgetEditResult.Updated>(add(HomeLayoutDefaults.standard(), GlanceWidgetKind.NOW_PLAYING))
        val widget = updated.layout.selectedPage.items.filterIsInstance<WidgetItem>().single()
        assertEquals(GlanceWidgetKind.NOW_PLAYING, widget.glance)
        assertEquals(GlanceWidgets.UNBOUND_HOST_ID, widget.appWidgetId)
        assertEquals("Now playing", widget.label)
        assertEquals(GlanceWidgetKind.NOW_PLAYING.defaultSpan, widget.placement?.span)
        assertEquals(GridCell(0, 0), widget.placement?.cell)
        assertEquals(GlanceWidgetKind.NOW_PLAYING.resizeConstraints, widget.resizeConstraints)
    }

    @Test
    fun severalOfAKindGetDistinctIdsAndNeverShareCells() {
        var layout: HomeLayout = HomeLayoutDefaults.standard()
        repeat(2) { layout = assertIs<WidgetEditResult.Updated>(add(layout, GlanceWidgetKind.NEXT_EVENT)).layout }
        val widgets = layout.selectedPage.items.filterIsInstance<WidgetItem>()
        assertEquals(2, widgets.map { it.id }.distinct().size)
        assertEquals(2, widgets.map { it.placement?.cell }.distinct().size)
    }

    @Test
    fun aFullPageRejectsWithNoAvailableCell() {
        val grid = GridDimensions(2, 1)
        val standard = HomeLayoutDefaults.standard()
        val layout = standard.copy(pages = listOf(standard.selectedPage.copy(grid = grid, items = emptyList())))
        val first = assertIs<WidgetEditResult.Updated>(add(layout, GlanceWidgetKind.TOP_STORY)).layout
        val second = add(first, GlanceWidgetKind.TOP_STORY)
        assertEquals(PlacementRejectionReason.NO_AVAILABLE_CELL, assertIs<WidgetEditResult.Rejected>(second).reason)
    }

    @Test
    fun aWidgetShrinksOnlyWithinItsMinimum() {
        val placed =
            assertIs<WidgetEditResult.Updated>(add(HomeLayoutDefaults.standard(), GlanceWidgetKind.TOP_STORY)).layout
        val id = placed.selectedPage.items.single().id
        assertIs<WidgetEditResult.Updated>(engine.resizeWidgetOnSelectedPage(placed, id, GridSpan(2, 1)))
        assertIs<WidgetEditResult.Rejected>(engine.resizeWidgetOnSelectedPage(placed, id, GridSpan(1, 1)))
        assertIs<WidgetEditResult.Rejected>(engine.resizeWidgetOnSelectedPage(placed, id, GridSpan(4, 5)))
    }

    private fun add(
        layout: HomeLayout,
        kind: GlanceWidgetKind,
    ): WidgetEditResult = engine.addGlanceWidgetToPage(layout, kind, kind.defaultLabel)

    @Test
    fun nowPlayingShowsTheNewestMediaAndNothingElse() {
        val older = media("m1", at = 10)
        val newer = media("m2", at = 20)
        val state = GlanceWidgets.stateFor(GlanceWidgetKind.NOW_PLAYING, ready(older, event("e", 5), newer))
        assertEquals(GlanceState.Showing(newer), state)
        assertEquals(GlanceState.Empty, GlanceWidgets.stateFor(GlanceWidgetKind.NOW_PLAYING, ready(event("e", 5))))
    }

    @Test
    fun nextEventShowsTheEarliestUpcomingEventAndSkipsAlarms() {
        val alarm = event("alarm", 1, EventKind.ALARM)
        val later = event("later", 50)
        val sooner = event("sooner", 40)
        assertEquals(
            GlanceState.Showing(sooner),
            GlanceWidgets.stateFor(GlanceWidgetKind.NEXT_EVENT, ready(alarm, later, sooner)),
        )
        assertEquals(GlanceState.Empty, GlanceWidgets.stateFor(GlanceWidgetKind.NEXT_EVENT, ready(alarm)))
    }

    @Test
    fun topStoryShowsTheNewestUnreadArticle() {
        val read = article("a1", published = 90, read = true)
        val old = article("a2", published = 10)
        val fresh = article("a3", published = 30)
        assertEquals(
            GlanceState.Showing(fresh),
            GlanceWidgets.stateFor(GlanceWidgetKind.TOP_STORY, ready(read, old, fresh)),
        )
        assertEquals(GlanceState.Empty, GlanceWidgets.stateFor(GlanceWidgetKind.TOP_STORY, ready(read)))
    }

    @Test
    fun aRedactedItemStaysRedactedAndKeepsItsPlace() {
        val hidden = media("m1", at = 1).copy(title = null, artist = null, privacy = StreamPrivacy.REDACTED)
        val shown = assertIs<GlanceState.Showing>(GlanceWidgets.stateFor(GlanceWidgetKind.NOW_PLAYING, ready(hidden)))
        assertEquals(StreamPrivacy.REDACTED, shown.content.privacy)
    }

    @Test
    fun everyOtherSectionResultIsOneHonestState() {
        val kind = GlanceWidgetKind.NOW_PLAYING
        assertEquals(GlanceState.Loading, GlanceWidgets.stateFor(kind, null))
        assertEquals(GlanceState.Loading, GlanceWidgets.stateFor(kind, StreamSectionResult.Loading))
        assertEquals(GlanceState.Empty, GlanceWidgets.stateFor(kind, StreamSectionResult.Empty))
        assertEquals(GlanceState.Off, GlanceWidgets.stateFor(kind, StreamSectionResult.Off))
        assertEquals(GlanceState.Unavailable, GlanceWidgets.stateFor(kind, StreamSectionResult.Unavailable))
        assertEquals(GlanceState.Unavailable, GlanceWidgets.stateFor(kind, StreamSectionResult.NotConnected()))
        val sources = listOf(SourceId("media"))
        assertEquals(
            GlanceState.PermissionRequired(sources),
            GlanceWidgets.stateFor(kind, StreamSectionResult.PermissionRequired(sources)),
        )
    }

    private fun ready(vararg content: com.riffle.core.domain.launcher.stream.StreamContent) =
        StreamSectionResult.Ready(listOf(StreamGroup("g", null, content.toList())))

    private fun media(
        id: String,
        at: Long,
    ) = MediaContent(
        id = StreamContentId(id),
        app = StreamAppRef("music"),
        appLabel = "Music",
        title = "Title $id",
        artist = "Artist",
        artwork = null,
        icon = null,
        isPlaying = true,
        target = ItemTarget.None,
        actions = MediaAction.entries.toSet(),
        postedAtEpochMillis = at,
        privacy = StreamPrivacy.VISIBLE,
    )

    private fun event(
        id: String,
        start: Long,
        kind: EventKind = EventKind.CALENDAR,
    ) = EventContent(
        id = StreamContentId(id),
        kind = kind,
        title = "Event $id",
        location = null,
        calendarName = null,
        startEpochMillis = start,
        endEpochMillis = start + 10,
        allDay = false,
        recurring = false,
        target = ItemTarget.None,
        actions = setOf(EventAction.OPEN),
        privacy = StreamPrivacy.VISIBLE,
    )

    private fun article(
        id: String,
        published: Long,
        read: Boolean = false,
    ) = ArticleContent(
        id = StreamContentId(id),
        feedId = "f",
        feedName = "Feed",
        title = "Story $id",
        summary = null,
        author = null,
        image = null,
        publishedAtEpochMillis = published,
        stale = false,
        target = ItemTarget.None,
        actions = setOf(ArticleAction.OPEN),
        privacy = StreamPrivacy.VISIBLE,
        read = read,
    )
}

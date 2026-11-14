package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamPagesTest {
    private fun note(
        key: String,
        at: Long = 1,
    ) = NotificationContent(
        id = StreamContentId("n:$key:$at"),
        app = StreamAppRef(key.substringBefore(':'), key.substringAfter(':')),
        appLabel = key.substringBefore(':'),
        title = "t",
        text = "x",
        icon = null,
        image = null,
        postedAtEpochMillis = at,
        category = null,
        priority = null,
        target = ItemTarget.None,
        actions = emptySet(),
        privacy = StreamPrivacy.VISIBLE,
    )

    private fun article(
        id: String,
        read: Boolean = false,
        feed: String = "feed",
    ) = ArticleContent(
        id = StreamContentId("a:$id"),
        feedId = feed,
        feedName = feed,
        title = id,
        summary = null,
        author = null,
        image = null,
        publishedAtEpochMillis = 5,
        stale = false,
        target = ItemTarget.None,
        actions = emptySet(),
        privacy = StreamPrivacy.VISIBLE,
        read = read,
    )

    private fun task(id: String) = TaskContent(StreamContentId("t:$id"), id)

    private fun notifications(vararg apps: String) =
        StreamSectionResult.Ready(
            apps.mapIndexed { i, key -> StreamGroup(key, key.substringBefore(':'), listOf(note(key, 10L - i)), 3) },
        )

    private fun ready(
        key: String,
        vararg items: StreamContent,
    ) = StreamSectionResult.Ready(listOf(StreamGroup(key, null, items.toList())))

    private val everything: Map<StreamSection, StreamSectionResult> =
        mapOf(
            StreamSection.NOW to ready("now", task("media")),
            StreamSection.NOTIFICATIONS to notifications("chat:p", "mail:p", "maps:p"),
            StreamSection.RECENTS to ready("recents", task("r")),
            StreamSection.TODAY to
                StreamSectionResult.Ready(
                    listOf(
                        StreamGroup("2026-01-01", null, listOf(task("e"))),
                        StreamGroup(StreamGroupKeys.TASKS, null, listOf(task("a"), task("b"))),
                    ),
                ),
            StreamSection.FEEDS to
                StreamSectionResult.Ready(
                    listOf(
                        StreamGroup("f1", "One", listOf(article("1", feed = "f1"))),
                        StreamGroup("f2", "Two", listOf(article("2", feed = "f2"))),
                    ),
                ),
        )

    @Test
    fun pagesFollowTheSectionOrderWithOnePagePerAppAndFeed() {
        val keys = StreamPagePlanner.plan(StreamLayout.DEFAULT, everything).map { it.key }
        assertEquals(
            listOf(
                "now",
                "notifications/chat:p",
                "notifications/mail:p",
                "notifications/maps:p",
                "recents",
                "today",
                "feeds/f1",
                "feeds/f2",
            ),
            keys,
        )
    }

    @Test
    fun aReorderedLayoutReordersThePagesAndAHiddenSectionContributesNone() {
        val layout =
            listOf(
                StreamLayoutAction.SetEnabled(StreamSection.NOW, false),
                StreamLayoutAction.Move(StreamSection.TODAY, -3),
            ).fold(StreamLayout.DEFAULT, StreamLayoutReducer::reduce)
        val keys = StreamPagePlanner.plan(layout, everything).map { it.key }
        assertEquals("today", keys.first())
        assertFalse("now" in keys)
    }

    @Test
    fun anAppPageCarriesItsNameItsCountAndTheViewOfItsSection() {
        val layout =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, StreamView.LIST),
            )
        val page = StreamPagePlanner.plan(layout, everything).first { it.groupKey == "mail:p" }
        assertEquals("mail", page.label)
        assertEquals(3, page.count)
        assertEquals(StreamView.LIST, page.view)
        assertEquals(StreamSection.NOTIFICATIONS, page.section)
        assertTrue(page.lead is NotificationContent)
    }

    @Test
    fun everyStateIsOnePageForItsSectionWithItsAction() {
        val states =
            listOf(
                StreamSectionResult.Loading,
                StreamSectionResult.Empty,
                StreamSectionResult.Unavailable,
                StreamSectionResult.Off,
                StreamSectionResult.NotConnected(),
                StreamSectionResult.PermissionRequired(emptyList()),
            )
        for (state in states) {
            val withState = everything + (StreamSection.NOTIFICATIONS to state)
            val pages = StreamPagePlanner.plan(StreamLayout.DEFAULT, withState)
            val page = pages.single { it.section == StreamSection.NOTIFICATIONS }
            assertEquals("notifications", page.key)
            assertEquals(StreamPageBody.State(state), page.body)
        }
    }

    @Test
    fun aSectionNotObservedYetIsALoadingPage() {
        val pages = StreamPagePlanner.plan(StreamLayout.DEFAULT, emptyMap())
        assertEquals(StreamSection.layoutSections.size, pages.size)
        assertTrue(pages.all { it.body == StreamPageBody.State(StreamSectionResult.Loading) })
    }

    @Test
    fun feedsWithNothingUnreadHaveNoPageAndNoFeedsIsOneEmptyPage() {
        val partly =
            StreamSectionResult.Ready(
                listOf(
                    StreamGroup("f1", "One", listOf(article("1", read = true, feed = "f1"))),
                    StreamGroup("f2", "Two", listOf(article("2", feed = "f2"), article("3", read = true, feed = "f2"))),
                ),
            )
        assertEquals(
            listOf("feeds/f2"),
            StreamPagePlanner.plan(StreamLayout.DEFAULT, everything + (StreamSection.FEEDS to partly))
                .map { it.key }
                .filter { it.startsWith("feeds") },
        )
        val oneRead = StreamGroup("f1", "One", listOf(article("1", read = true, feed = "f1")))
        val allRead = StreamSectionResult.Ready(listOf(oneRead))
        val page = StreamPagePlanner.plan(StreamLayout.DEFAULT, everything + (StreamSection.FEEDS to allRead)).last()
        assertEquals("feeds", page.key)
        assertEquals(StreamPageBody.State(StreamSectionResult.Empty), page.body)
    }

    @Test
    fun aNewNotificationDoesNotChangeAnyOtherPagesKey() {
        val before = StreamPagePlanner.plan(StreamLayout.DEFAULT, everything).map { it.key }
        val withNew = everything + (StreamSection.NOTIFICATIONS to notifications("new:p", "chat:p", "mail:p", "maps:p"))
        val after = StreamPagePlanner.plan(StreamLayout.DEFAULT, withNew).map { it.key }
        assertEquals(before.toSet() + "notifications/new:p", after.toSet())
        // The new app is the newest, so its page leads the section; every other key is exactly as before.
        assertEquals(before.indexOf("notifications/chat:p") + 1, after.indexOf("notifications/chat:p"))
    }

    @Test
    fun keysAreUniqueEvenWhenAnAppIsCalledLikeASection() {
        val odd = everything + (StreamSection.NOTIFICATIONS to notifications("recents:p"))
        val keys = StreamPagePlanner.plan(StreamLayout.DEFAULT, odd).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    private val keys = listOf("a", "b", "c", "d", "e")

    @Test
    fun theCurrentPageStaysCurrentWhilePagesAreAddedOrRemovedAroundIt() {
        assertEquals("c", StreamPageNavigation.currentAfter(keys, "c", listOf("x", "a", "c", "y")))
        assertEquals("c", StreamPageNavigation.currentAfter(keys, "c", listOf("c")))
        assertEquals("c", StreamPageNavigation.currentAfter(keys, "c", listOf("e", "d", "c", "b", "a")))
    }

    @Test
    fun aVanishedCurrentPageGoesToItsNearestNeighbourPreferringTheNextOne() {
        assertEquals("d", StreamPageNavigation.currentAfter(keys, "c", listOf("a", "b", "d", "e")))
        assertEquals("b", StreamPageNavigation.currentAfter(keys, "c", listOf("a", "b", "e")))
        assertEquals("e", StreamPageNavigation.currentAfter(keys, "d", listOf("a", "e")))
        assertEquals("b", StreamPageNavigation.currentAfter(keys, "e", listOf("a", "b")))
        assertEquals("x", StreamPageNavigation.currentAfter(keys, "c", listOf("x", "y")))
    }

    @Test
    fun noPagesMeansNoCurrentPageAndAnUnknownCurrentIsTheFirst() {
        assertNull(StreamPageNavigation.currentAfter(keys, "c", emptyList()))
        assertEquals("x", StreamPageNavigation.currentAfter(keys, null, listOf("x", "y")))
        assertEquals("x", StreamPageNavigation.currentAfter(keys, "zz", listOf("x", "y")))
    }

    @Test
    fun indexAndStepStayInsideThePages() {
        assertEquals(2, StreamPageNavigation.indexOf(keys, "c"))
        assertEquals(0, StreamPageNavigation.indexOf(keys, "zz"))
        assertEquals(4, StreamPageNavigation.indexOf(keys, "zz", fallback = 9))
        assertEquals(0, StreamPageNavigation.step(0, -1, 5))
        assertEquals(4, StreamPageNavigation.step(4, 1, 5))
        assertEquals(3, StreamPageNavigation.step(2, 1, 5))
        assertEquals(0, StreamPageNavigation.step(0, 1, 0))
    }

    private val pages = StreamPagePlanner.plan(StreamLayout.DEFAULT, everything)

    @Test
    fun focusLandsOnTheNamedPage() {
        assertEquals("now", StreamFocus.resolvePage(StreamFocusTarget.Now, pages))
        assertEquals("notifications/mail:p", StreamFocus.resolvePage(StreamFocusTarget.AppStack("mail:p"), pages))
        assertEquals("today", StreamFocus.resolvePage(StreamFocusTarget.Section(StreamSection.TODAY), pages))
        val notifications = StreamFocusTarget.Section(StreamSection.NOTIFICATIONS)
        assertEquals("notifications/chat:p", StreamFocus.resolvePage(notifications, pages))
        assertEquals("feeds/f1", StreamFocus.resolvePage(StreamFocusTarget.Section(StreamSection.FEEDS), pages))
    }

    @Test
    fun aVanishedAppLandsOnTheFirstNotificationsPageAndAHiddenSectionOnNothing() {
        assertEquals("notifications/chat:p", StreamFocus.resolvePage(StreamFocusTarget.AppStack("gone:p"), pages))
        val noNow = pages.filter { it.section != StreamSection.NOW }
        assertNull(StreamFocus.resolvePage(StreamFocusTarget.Now, noNow))
        val noNotifications = pages.filter { it.section != StreamSection.NOTIFICATIONS }
        assertNull(StreamFocus.resolvePage(StreamFocusTarget.AppStack("a:b"), noNotifications))
        assertNull(StreamFocus.resolvePage(StreamFocusTarget.Now, emptyList()))
    }

    @Test
    fun theStageShowsTheSelectedStageAndAllTheOthersInTheRail() {
        val model = StreamStage.model(pages, "notifications/mail:p")
        assertEquals("notifications/mail:p", model.selected?.key)
        assertEquals(pages.map { it.key } - "notifications/mail:p", model.rail.map { it.key })
    }

    @Test
    fun theStageJustLeftComesFirstInTheRailThenTheRestInPageOrder() {
        var recent = emptyList<String>()
        recent = StreamStage.recentAfterSelecting(recent, "now", "recents")
        recent = StreamStage.recentAfterSelecting(recent, "recents", "today")
        assertEquals(listOf("recents", "now"), recent)
        val model = StreamStage.model(pages, "today", recent)
        assertEquals(listOf("recents", "now"), model.rail.take(2).map { it.key })
        assertEquals(pages.size - 1, model.rail.size)
        // Coming back to a stage that is in the memory takes it out of it.
        assertEquals(listOf("today", "now"), StreamStage.recentAfterSelecting(recent, "today", "recents"))
    }

    @Test
    fun aVanishedStageHandsOverToItsNearestAndNothingIsEmptyWhenThereAreNoPages() {
        val without = pages.filter { it.key != "recents" }
        val model = StreamStage.model(without, "recents", previousKeys = pages.map { it.key })
        assertEquals("today", model.selected?.key)
        assertTrue(model.rail.none { it.key == "today" })
        assertEquals(StreamStageModel(null, emptyList()), StreamStage.model(emptyList(), "x"))
    }

    @Test
    fun theRailCollapsesToIconsInANarrowPaneAndTwoPagesShowOnlyOnAWholeWideScreen() {
        assertEquals(StreamRailMode.ICONS, StreamStage.railMode(StreamStage.RAIL_COLLAPSE_BELOW_DP - 1))
        assertEquals(StreamRailMode.FULL, StreamStage.railMode(StreamStage.RAIL_COLLAPSE_BELOW_DP))
        assertEquals(2, StreamPagesPerView.forWidth(900, wideView = true))
        assertEquals(1, StreamPagesPerView.forWidth(900, wideView = false))
        assertEquals(1, StreamPagesPerView.forWidth(700, wideView = true))
    }

    @Test
    fun theStackGeometryTokensAreCalmsCardAndStep() {
        assertEquals(168f, StreamCardStackSpec.CARD_HEIGHT_DP)
        assertEquals(56f, StreamCardStackSpec.PITCH_DP)
        // The strip left by a peek is the pitch less the gap, so it stays a whole touch target (48 dp).
        assertTrue(StreamCardStackSpec.PITCH_DP - StreamCardStackSpec.GAP_DP >= 48f)
    }

    @Test
    fun aStacksOlderCardsPeekAboveTheFrontOneAndTheNewerOneSinksBelowIt() {
        val geometry = StackGeometry(StreamCardStackSpec.CARD_HEIGHT_DP)
        val front = geometry.frame(0f)
        assertTrue(geometry.frame(1f).topDp > front.topDp)
        assertTrue(geometry.frame(-0.5f).topDp < front.topDp)
        assertEquals(56f, geometry.frame(1f).topDp - front.topDp)
    }
}

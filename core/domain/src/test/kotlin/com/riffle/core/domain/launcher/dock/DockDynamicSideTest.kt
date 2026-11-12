package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DockDynamicSideTest {
    @Test
    fun withNothingNewTheSideHoldsOnlyTheStreamButton() {
        assertEquals(listOf(DockDynamicSideEntry.StreamButton), DockDynamicSide.entries(emptyList(), null, 3))
    }

    @Test
    fun liveAppsAreNewestFirstThenNowPlayingThenTheStreamButtonLast() {
        val entries =
            DockDynamicSide.entries(
                listOf(
                    group("mail", posted = 100, total = 7),
                    group("chat", posted = 300),
                    group("news", posted = 200),
                ),
                media("music", title = "Song"),
                liveAppSlots = 3,
            )
        assertEquals(
            listOf("live:chat:personal", "live:news:personal", "live:mail:personal", "now", "stream"),
            entries.map { it.key },
        )
        val mail = entries[2] as DockDynamicSideEntry.LiveApp
        assertEquals(7, mail.count)
        assertEquals("Mail", mail.label)
        assertEquals("Song", (entries[3] as DockDynamicSideEntry.NowPlaying).title)
    }

    @Test
    fun theSlotsLimitLiveAppsButNeverNowPlayingOrTheButton() {
        val groups = (1..5).map { group("app$it", posted = it * 10L) }
        val entries = DockDynamicSide.entries(groups, media("music"), liveAppSlots = 2)
        assertEquals(listOf("live:app5:personal", "live:app4:personal", "now", "stream"), entries.map { it.key })
        assertEquals(DockDynamicSide.FIXED_ENTRY_COUNT, entries.count { it !is DockDynamicSideEntry.LiveApp })
    }

    @Test
    fun zeroSlotsLeavesNowPlayingAndTheButton() {
        val entries = DockDynamicSide.entries(listOf(group("chat", 1)), media("music"), liveAppSlots = 0)
        assertEquals(listOf("now", "stream"), entries.map { it.key })
    }

    @Test
    fun slotsOutsideTheRangeAreClamped() {
        val groups = (1..9).map { group("app$it", posted = it.toLong()) }
        assertEquals(5 + 1, DockDynamicSide.entries(groups, null, liveAppSlots = 99).size)
        assertEquals(1, DockDynamicSide.entries(groups, null, liveAppSlots = -4).size)
    }

    @Test
    fun equalTimesBreakTiesByGroupKey() {
        val entries = DockDynamicSide.entries(listOf(group("b", 5), group("a", 5)), null, 2)
        assertEquals(listOf("live:a:personal", "live:b:personal", "stream"), entries.map { it.key })
    }

    @Test
    fun aRedactedNowPlayingHasNoTitle() {
        val entries =
            DockDynamicSide.entries(
                emptyList(),
                media("music", title = "Secret", privacy = StreamPrivacy.REDACTED),
                3,
            )
        assertEquals(null, assertIs<DockDynamicSideEntry.NowPlaying>(entries.first()).title)
    }

    @Test
    fun aGroupWithoutNotificationContentIsSkipped() {
        val articles = StreamGroup(key = "feed", label = "Feed", items = listOf(media("music")))
        assertEquals(listOf(DockDynamicSideEntry.StreamButton), DockDynamicSide.entries(listOf(articles), null, 3))
    }

    private fun group(
        pkg: String,
        posted: Long,
        total: Int = 1,
    ): StreamGroup {
        val item =
            NotificationContent(
                id = StreamContentId("n:$pkg"),
                app = StreamAppRef(pkg, "personal"),
                appLabel = pkg.replaceFirstChar { it.uppercase() },
                title = "t",
                text = "x",
                icon = null,
                image = null,
                postedAtEpochMillis = posted,
                category = null,
                priority = null,
                target = ItemTarget.App(pkg, "personal"),
                actions = setOf(NotificationAction.OPEN),
                privacy = StreamPrivacy.VISIBLE,
            )
        return StreamGroup(
            key = "$pkg:personal",
            label =
                pkg.replaceFirstChar {
                    it.uppercase()
                },
            items = listOf(item),
            totalCount = total,
        )
    }

    private fun media(
        pkg: String,
        title: String? = "Track",
        privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
    ) = MediaContent(
        id = StreamContentId("m:$pkg"),
        app = StreamAppRef(pkg, "personal"),
        appLabel = "Music",
        title = title,
        artist = null,
        artwork = null,
        icon = null,
        target = ItemTarget.App(pkg, "personal"),
        actions = emptySet(),
        privacy = privacy,
    )
}

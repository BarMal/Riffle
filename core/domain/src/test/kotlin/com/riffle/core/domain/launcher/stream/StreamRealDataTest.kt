package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.AppItemMapper
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionItems
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionQueueEntry
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P3b: the typed content carries real data (threads, reply, media sessions, read state, frequent apps). */
class StreamRealDataTest {
    private val personal = AppProfile.personal().id
    private val visible = mapOf(personal to AppProfileContentVisibility.VISIBLE)

    private fun message(
        sender: String,
        text: String,
        at: Long,
    ) = LauncherNotificationMessage(sender, text, at)

    private fun messaging(
        messages: List<LauncherNotificationMessage>,
        media: Boolean = false,
    ) = LauncherNotification(
        key = LauncherNotificationKey("k1"),
        packageName = AppPackageName("chat"),
        canDismiss = true,
        isMediaSession = media,
        title = "Group",
        text = "latest",
        messages = messages,
        postedAtEpochMillis = NOW - 10,
    )

    private fun notificationItems(
        notification: LauncherNotification,
        reply: Boolean = true,
        snooze: Boolean = true,
        visibility: Map<AppProfileId, AppProfileContentVisibility> = visible,
    ): List<Item> =
        NotificationItemMapper().notificationItems(
            NotificationItemInput(
                notifications = listOf(notification),
                nowEpochMillis = NOW,
                profileContentVisibility = visibility,
                canReply = { reply },
                canSnooze = { snooze },
            ),
        )

    private val thread =
        listOf(message("Ann", "one", 1), message("Bob", "two", 2), message("Ann", "three", 3))

    @Test
    fun `a messaging notification exposes its thread, reply and snooze`() {
        val item = notificationItems(messaging(thread)).single()
        val content = assertIs<NotificationContent>(StreamContentMapper.map(item))

        assertEquals(listOf("one", "two", "three"), content.thread.map { it.text })
        assertEquals("Bob", content.thread[1].sender)
        assertEquals("k1", content.notificationKey)
        assertEquals(
            setOf(
                NotificationAction.OPEN,
                NotificationAction.DISMISS,
                NotificationAction.REPLY,
                NotificationAction.SNOOZE,
                NotificationAction.HIDE,
            ),
            content.actions,
        )
    }

    @Test
    fun `reply and snooze are only offered when the platform can do them, and one message is not a thread`() {
        val item =
            notificationItems(
                messaging(listOf(message("Ann", "one", 1))),
                reply = false,
                snooze = false,
            ).single()
        val content = assertIs<NotificationContent>(StreamContentMapper.map(item))
        assertEquals(emptyList(), content.thread)
        assertFalse(NotificationAction.REPLY in content.actions)
        assertFalse(NotificationAction.SNOOZE in content.actions)
    }

    @Test
    fun `a thread is capped to the newest messages`() {
        val many = (1..25).map { message("Ann", "m$it", it.toLong()) }
        val content =
            assertIs<NotificationContent>(StreamContentMapper.map(notificationItems(messaging(many)).single()))
        assertEquals(NotificationItemMapper.MAX_THREAD, content.thread.size)
        assertEquals("m25", content.thread.last().text)
        assertEquals("m16", content.thread.first().text)
    }

    @Test
    fun `sensitive and hidden notifications expose no history, no reply text and no key`() {
        val quiet = mapOf(personal to AppProfileContentVisibility.REDACTED_QUIET)
        val sensitive = notificationItems(messaging(thread), visibility = quiet).single()
        val fromQuiet = assertIs<NotificationContent>(StreamContentMapper.map(sensitive))
        assertEquals(emptyList(), fromQuiet.thread)
        assertNull(fromQuiet.notificationKey)
        assertEquals(setOf(NotificationAction.HIDE), fromQuiet.actions)
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(fromQuiet, StreamAction.Reply("hi")))

        val item = notificationItems(messaging(thread)).single()
        val hideText = SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)
        val hidden = assertIs<NotificationContent>(StreamContentMapper.map(item, hideText))
        assertEquals(emptyList(), hidden.thread)
        assertNull(hidden.notificationKey)
        assertEquals(setOf(NotificationAction.HIDE), hidden.actions)

        val hideBody = SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_BODY)
        assertEquals(emptyList(), assertIs<NotificationContent>(StreamContentMapper.map(item, hideBody)).thread)
    }

    @Test
    fun `a media session never carries a thread or a key`() {
        val item =
            NotificationItemMapper().mediaItems(
                NotificationItemInput(listOf(messaging(thread, media = true)), NOW, profileContentVisibility = visible),
            ).single()
        assertTrue(item.ext.keys.none { it.value.startsWith("notification.m") || it.value == "notification.key" })
    }

    // --- Media ------------------------------------------------------------------------------------------------

    private val player =
        MediaSessionInfo(
            packageName = "player",
            title = "Song",
            artist = "Band",
            hasArtwork = true,
            isPlaying = true,
            positionMillis = 30_000,
            durationMillis = 200_000,
            controls = setOf(MediaControl.PAUSE, MediaControl.NEXT, MediaControl.SEEK),
            queue = listOf(MediaSessionQueueEntry("Next song", "Band"), MediaSessionQueueEntry(null)),
        )

    private fun mediaItem(privacy: ItemPrivacy = ItemPrivacy.VISIBLE) =
        notificationItem("m", pkg = "player", source = SourceIds.MEDIA, privacy = privacy)

    @Test
    fun `a media session fills play state, position, queue and the transport actions that are really offered`() {
        val item = MediaSessionItems.enrich(listOf(mediaItem()), listOf(player)).single()
        val media = assertIs<MediaContent>(StreamContentMapper.map(item))

        assertEquals(true, media.isPlaying)
        assertEquals(30_000L, media.positionMillis)
        assertEquals(200_000L, media.durationMillis)
        assertEquals(listOf(MediaQueueEntry("Next song", "Band")), media.queue)
        assertEquals(setOf(MediaAction.OPEN, MediaAction.PAUSE, MediaAction.NEXT, MediaAction.SEEK), media.actions)
        assertEquals("Title m", media.title)
    }

    @Test
    fun `a session with no matching visible media item is ignored, and a sensitive item is never enriched`() {
        assertEquals(emptyList(), MediaSessionItems.enrich(emptyList(), listOf(player)))
        val other =
            mediaItem().copy(
                target = ItemTarget.App("other", "personal"),
            )
        assertEquals(listOf(other), MediaSessionItems.enrich(listOf(other), listOf(player)))
        val sensitive = mediaItem(ItemPrivacy.SENSITIVE)
        assertEquals(listOf(sensitive), MediaSessionItems.enrich(listOf(sensitive), listOf(player)))
    }

    @Test
    fun `media queue follows the body level and transport follows hide text`() {
        val item = MediaSessionItems.enrich(listOf(mediaItem()), listOf(player)).single()

        val body = SourceContentLevels().with(SourceIds.MEDIA, SourceContentLevel.HIDE_BODY)
        val noQueue = assertIs<MediaContent>(StreamContentMapper.map(item, body))
        assertEquals(emptyList(), noQueue.queue)
        assertTrue(MediaAction.NEXT in noQueue.actions)

        val text = SourceContentLevels().with(SourceIds.MEDIA, SourceContentLevel.HIDE_TEXT)
        val hidden = assertIs<MediaContent>(StreamContentMapper.map(item, text))
        assertEquals(emptyList(), hidden.queue)
        assertNull(hidden.title)
        assertEquals(null, hidden.isPlaying)
        assertEquals(emptySet(), hidden.actions)
    }

    @Test
    fun `the session only fills what the notification left blank`() {
        val bare = mediaItem().copy(title = null, subtitle = null, image = null)
        val item = MediaSessionItems.enrich(listOf(bare), listOf(player)).single()
        assertEquals("Song", item.title)
        assertEquals("Band", item.subtitle)
        assertEquals("media-art:player", item.image?.key)
    }

    // --- Actions ------------------------------------------------------------------------------------------------

    @Test
    fun `the policy only lets through what the content offers`() {
        val media =
            assertIs<MediaContent>(
                StreamContentMapper.map(MediaSessionItems.enrich(listOf(mediaItem()), listOf(player)).single()),
            )
        assertEquals(null, StreamActionPolicy.check(media, StreamAction.Pause))
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(media, StreamAction.Play))
        assertEquals(null, StreamActionPolicy.check(media, StreamAction.SeekTo(10_000)))
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(media, StreamAction.SeekTo(999_999)))
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(media, StreamAction.Dismiss))

        val note = assertIs<NotificationContent>(StreamContentMapper.map(notificationItems(messaging(thread)).single()))
        assertEquals(null, StreamActionPolicy.check(note, StreamAction.Reply("hello")))
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(note, StreamAction.Reply("  ")))
        assertEquals(null, StreamActionPolicy.check(note, StreamAction.Snooze()))
        assertEquals(StreamActionResult.NotOffered, StreamActionPolicy.check(note, StreamAction.MarkRead))
    }

    // --- Articles -----------------------------------------------------------------------------------------------

    @Test
    fun `an unread article offers mark read and a read one does not`() {
        fun article(read: Boolean) =
            articleItem("a", "Feed", NOW).let { item ->
                item.copy(
                    ext =
                        item.ext +
                            mapOf(
                                StreamContentMapper.ARTICLE_DIGEST to ItemExtValue.Text("d1"),
                                StreamContentMapper.ARTICLE_READ to ItemExtValue.Flag(read),
                            ),
                )
            }

        val unread = assertIs<ArticleContent>(StreamContentMapper.map(article(read = false)))
        assertEquals(setOf(ArticleAction.OPEN, ArticleAction.MARK_READ), unread.actions)
        assertEquals("d1", unread.digest)
        assertEquals(null, StreamActionPolicy.check(unread, StreamAction.MarkRead))

        val read = assertIs<ArticleContent>(StreamContentMapper.map(article(read = true)))
        assertEquals(setOf(ArticleAction.OPEN), read.actions)
        assertTrue(read.read)

        // No digest (a source that does not track read state, or hidden text): no action.
        val plain = assertIs<ArticleContent>(StreamContentMapper.map(articleItem("b", "Feed", NOW)))
        assertEquals(setOf(ArticleAction.OPEN), plain.actions)
    }

    // --- Recents and frequent apps ----------------------------------------------------------------------------

    @Test
    fun `recents add the most used other apps as a second group`() {
        val apps =
            (1..6).map { recentItem("p$it", NOW - it * 10L) }.map { item ->
                // p1 is the most recent; p5 and p6 are old but heavily used; p4 is old and barely used.
                val foreground =
                    mapOf(
                        "p1" to 5L,
                        "p4" to 1L,
                        "p5" to 900L,
                        "p6" to 500L,
                    )[(item.target as ItemTarget.App).packageName]
                if (foreground == null) {
                    item
                } else {
                    item.copy(
                        ext = item.ext + (AppItemMapper.FOREGROUND_KEY to ItemExtValue.Number(foreground)),
                    )
                }
            }
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.RECENTS,
                StreamSectionInputs(
                    mapOf(SourceIds.RECENT_APPS to SourceState.Ready(apps)),
                ),
                StreamContext(NOW, StreamSectionOptions(recents = RecentsOptions(limit = 3, frequentLimit = 2))),
            )
        val ready = assertIs<StreamSectionResult.Ready>(result)
        assertEquals(listOf("recents", "frequent"), ready.groups.map { it.key })
        assertEquals(listOf("p1", "p2", "p3"), ready.groups[0].items.map { (it as RecentAppContent).app.packageName })
        assertEquals(listOf("p5", "p6"), ready.groups[1].items.map { (it as RecentAppContent).app.packageName })
    }

    @Test
    fun `frequent apps can be turned off, and the usage foreground time reaches the item`() {
        val usage = RecentAppUsage(AppPackageName("p"), lastUsedAtMillis = 5, foregroundMillis = 42)
        assertEquals(42L, usage.foregroundMillis)
        assertEquals(0L, RecentAppUsage(AppPackageName("p"), 5).foregroundMillis)

        val apps =
            listOf(recentItem("a", 3), recentItem("b", 2)).map {
                it.copy(ext = it.ext + (AppItemMapper.FOREGROUND_KEY to ItemExtValue.Number(10)))
            }
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.RECENTS,
                StreamSectionInputs(
                    mapOf(SourceIds.RECENT_APPS to SourceState.Ready(apps)),
                ),
                StreamContext(NOW, StreamSectionOptions(recents = RecentsOptions(limit = 1, frequentLimit = 0))),
            )
        assertEquals(listOf("recents"), assertIs<StreamSectionResult.Ready>(result).groups.map { it.key })
    }

    // --- Agenda -------------------------------------------------------------------------------------------------

    @Test
    fun `today reads its own calendar source but reports the user-facing calendar as the missing permission`() {
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.TODAY,
                StreamSectionInputs(
                    mapOf(
                        StreamSourceIds.AGENDA to SourceState.PermissionRequired,
                        SourceIds.ICS to SourceState.Ready(emptyList()),
                    ),
                    tasks = TypedSourceState.Off,
                ),
                StreamContext(NOW),
            )
        // The feeds are readable but empty; the calendar permission is the gap to report.
        assertEquals(StreamSectionResult.PermissionRequired(listOf(SourceIds.CALENDAR)), result)
    }

    @Test
    fun `today lists many events across the week, not only the next few`() {
        val day = 24 * HOUR
        val events = (1..12).map { eventItem("e$it", NOW + it * 12 * HOUR) }
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.TODAY,
                StreamSectionInputs(
                    mapOf(
                        StreamSourceIds.AGENDA to SourceState.Ready(events),
                        SourceIds.ICS to SourceState.Ready(emptyList()),
                    ),
                    tasks = TypedSourceState.Off,
                ),
                StreamContext(NOW),
            )
        val ready = assertIs<StreamSectionResult.Ready>(result)
        assertEquals(12, ready.items.size)
        assertTrue(ready.groups.size > 1)
        assertTrue(ready.items.all { it.timeEpochMillis!! < NOW + 7 * day })
    }
}

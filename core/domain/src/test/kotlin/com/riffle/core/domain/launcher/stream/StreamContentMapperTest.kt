package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.CalendarItemMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamContentMapperTest {
    @Test
    fun `a notification keeps its fields and offers its actions`() {
        val content = StreamContentMapper.map(notificationItem("a", priority = 4))

        assertIs<NotificationContent>(content)
        assertEquals(StreamAppRef("chat", "personal"), content.app)
        assertEquals("Chat", content.appLabel)
        assertEquals("Title a", content.title)
        assertEquals("Text a", content.text)
        assertEquals(NotificationCategory.MESSAGE, content.category)
        assertEquals(NotificationPriority.HIGH, content.priority)
        assertEquals(
            setOf(NotificationAction.OPEN, NotificationAction.DISMISS, NotificationAction.HIDE),
            content.actions,
        )
        assertEquals(StreamPrivacy.VISIBLE, content.privacy)
    }

    @Test
    fun `a sensitive notification loses its text, image and actions but keeps identity`() {
        val content =
            StreamContentMapper.map(notificationItem("a", privacy = ItemPrivacy.SENSITIVE)) as NotificationContent

        assertEquals(StreamPrivacy.REDACTED, content.privacy)
        assertNull(content.title)
        assertNull(content.text)
        assertNull(content.image)
        assertNull(content.category)
        assertEquals(setOf(NotificationAction.HIDE), content.actions)
        assertEquals("Chat", content.appLabel)
        assertEquals(NOW - 10, content.postedAtEpochMillis)
        assertEquals(ItemTarget.App("chat", "personal"), content.target)
    }

    @Test
    fun `media drops the artist and artwork at hide body and every text at hide text`() {
        val item = notificationItem("m", source = SourceIds.MEDIA)
        val shown = StreamContentMapper.map(item) as MediaContent
        assertEquals("Artist m", shown.artist)
        assertEquals(emptyList(), shown.queue)
        assertEquals(setOf(MediaAction.OPEN), shown.actions)

        val body = SourceContentLevels().with(SourceIds.MEDIA, SourceContentLevel.HIDE_BODY)
        val noArtist = StreamContentMapper.map(item, body) as MediaContent
        assertEquals("Title m", noArtist.title)
        assertNull(noArtist.artist)
        assertNull(noArtist.artwork)
        assertEquals(StreamPrivacy.VISIBLE, noArtist.privacy)

        val text = SourceContentLevels().with(SourceIds.MEDIA, SourceContentLevel.HIDE_TEXT)
        val hidden = StreamContentMapper.map(item, text) as MediaContent
        assertNull(hidden.title)
        assertEquals(StreamPrivacy.REDACTED, hidden.privacy)
        assertEquals("Chat", hidden.appLabel)
    }

    @Test
    fun `a private calendar event keeps its start but not its text or end`() {
        val items =
            CalendarItemMapper().nextEvents(
                listOf(CalendarEvent("p", "Doctor", 2_000, 3_000, location = "Clinic", isPrivate = true)),
                nowEpochMillis = 0,
            )
        val event = StreamContentMapper.map(items.single()) as EventContent

        assertEquals(EventKind.CALENDAR, event.kind)
        assertEquals(StreamPrivacy.REDACTED, event.privacy)
        assertNull(event.title)
        assertNull(event.location)
        assertNull(event.endEpochMillis)
        assertEquals(2_000, event.startEpochMillis)
    }

    @Test
    fun `a visible calendar event carries end, location and all day`() {
        val items =
            CalendarItemMapper().nextEvents(
                listOf(CalendarEvent("e", "Standup", 2_000, 3_000, location = "Room 1", allDay = true)),
                nowEpochMillis = 0,
            )
        val event = StreamContentMapper.map(items.single()) as EventContent

        assertEquals("Standup", event.title)
        assertEquals("Room 1", event.location)
        assertEquals(3_000L, event.endEpochMillis)
        assertTrue(event.allDay)
        assertTrue(EventAction.OPEN in event.actions)
    }

    @Test
    fun `a calendar feed event does not mistake the feed name for a location`() {
        val named = eventItem("f", 10, source = SourceIds.ICS, feed = "Holidays").copy(subtitle = "Holidays")
        val event = StreamContentMapper.map(named) as EventContent

        assertEquals(EventKind.CALENDAR_FEED, event.kind)
        assertNull(event.location)
        assertEquals("Holidays", event.calendarName)
    }

    @Test
    fun `an article maps its feed, author and lazy image`() {
        val article = StreamContentMapper.map(articleItem("x", "news", published = 5)) as ArticleContent

        assertEquals("news", article.feedId)
        assertEquals("Ann", article.author)
        assertEquals("Summary x", article.summary)
        assertEquals(setOf(ArticleAction.OPEN), article.actions)
    }

    @Test
    fun `a recent app offers the launcher actions and an item without an app target is skipped`() {
        val recent = StreamContentMapper.map(recentItem("mail", 9)) as RecentAppContent
        assertEquals(RecentAppAction.entries.toSet(), recent.actions)

        assertNull(StreamContentMapper.map(recentItem("mail", 9).copy(target = ItemTarget.None)))
        assertNull(StreamContentMapper.map(recentItem("mail", 9).copy(sourceId = SourceIds.SEARCH)))
    }

    @Test
    fun `tasks and alarms follow their own content level`() {
        val hidden =
            SourceContentLevels().with(StreamSourceIds.TASKS, SourceContentLevel.HIDE_TEXT)
                .with(StreamSourceIds.ALARM, SourceContentLevel.HIDE_TEXT)

        val task = StreamContentMapper.redact(TaskContent(StreamContentId("t"), "Pay rent"), hidden)
        assertNull(task.title)
        assertEquals(StreamPrivacy.REDACTED, task.privacy)

        val alarm = StreamContentMapper.alarm(NextAlarm("a", 5, "Gym"), hidden)
        assertNull(alarm.title)
        assertEquals(EventKind.ALARM, alarm.kind)
        assertEquals("Gym", StreamContentMapper.alarm(NextAlarm("a", 5, "Gym"), SourceContentLevels.ALL_SHOWN).title)
    }
}

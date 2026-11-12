package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.ExclusionHideActions
import com.riffle.core.domain.launcher.workspace.settings.HideKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamPresentationTest {
    private fun event(
        start: Long,
        end: Long? = start + HOUR,
        allDay: Boolean = false,
        kind: EventKind = EventKind.CALENDAR,
    ) = EventContent(
        id = StreamContentId("e"),
        kind = kind,
        title = "Standup",
        location = null,
        calendarName = null,
        startEpochMillis = start,
        endEpochMillis = end,
        allDay = allDay,
        recurring = false,
        target = ItemTarget.None,
        actions = setOf(EventAction.OPEN),
        privacy = StreamPrivacy.VISIBLE,
    )

    @Test
    fun anUpcomingEventCountsDownInWholeMinutesRoundedUp() {
        assertEquals(EventTiming.Upcoming(12), EventTiming.of(event(NOW + 12 * 60_000L), NOW))
        assertEquals(EventTiming.Upcoming(13), EventTiming.of(event(NOW + 12 * 60_000L + 1), NOW))
        assertEquals(EventTiming.Upcoming(1), EventTiming.of(event(NOW + 1), NOW))
    }

    @Test
    fun anEventInProgressCountsTheTimeLeft() {
        assertEquals(EventTiming.Ongoing(30), EventTiming.of(event(NOW - HOUR / 2, NOW + HOUR / 2), NOW))
        assertEquals(EventTiming.Ongoing(null), EventTiming.of(event(NOW - 1, null), NOW))
    }

    @Test
    fun finishedAllDayAndAlarmEvents() {
        assertEquals(EventTiming.Ended, EventTiming.of(event(NOW - 2 * HOUR, NOW - HOUR), NOW))
        assertEquals(EventTiming.AllDay, EventTiming.of(event(NOW, null, allDay = true), NOW))
        assertEquals(EventTiming.Ended, EventTiming.of(event(NOW - 1, null, kind = EventKind.ALARM), NOW))
        assertEquals(EventTiming.Upcoming(60), EventTiming.of(event(NOW + HOUR, null, kind = EventKind.ALARM), NOW))
    }

    @Test
    fun mediaProgressMapsPositionsBothWays() {
        assertEquals(0.5f, MediaProgress.fraction(30_000, 60_000))
        assertNull(MediaProgress.fraction(null, 60_000))
        assertNull(MediaProgress.fraction(10, 0))
        assertEquals(1f, MediaProgress.fraction(90_000, 60_000))
        assertEquals(30_000L, MediaProgress.positionAt(0.5f, 60_000))
        assertEquals(60_000L, MediaProgress.positionAt(7f, 60_000))
        assertEquals(0L, MediaProgress.positionAt(-1f, 60_000))
    }

    @Test
    fun mediaClockFormatsMinutesAndHours() {
        assertEquals("0:05", MediaProgress.clock(5_000))
        assertEquals("3:07", MediaProgress.clock(187_000))
        assertEquals("1:02:03", MediaProgress.clock(3_723_000))
        assertEquals("0:00", MediaProgress.clock(-5))
    }

    @Test
    fun hideItemsCarryTheIdentityTheExclusionRulesMatchOn() {
        val notification =
            (StreamContentMapper.map(notificationItem("k1")) as NotificationContent)
        val item = assertNotNull(StreamHideItems.itemFor(notification, groupKey = "chat:personal"))
        assertEquals(SourceIds.NOTIFICATIONS, item.sourceId)
        assertEquals(notification.id.value, item.id.value)
        assertEquals("chat:personal", item.groupKey)
        val kinds = ExclusionHideActions.menuChoicesFor(item).map { it.kind }
        assertTrue(HideKind.APP in kinds && HideKind.ITEM in kinds && HideKind.GROUP in kinds)
    }

    @Test
    fun aRedactedContentHidesOnlyByStructure() {
        val redacted = (
            StreamContentMapper.map(
                notificationItem("k2", privacy = ItemPrivacy.SENSITIVE),
            ) as NotificationContent
        )
        val item = assertNotNull(StreamHideItems.itemFor(redacted, groupKey = "chat:personal"))
        assertEquals(ItemPrivacy.SENSITIVE, item.privacy)
        val kinds = ExclusionHideActions.menuChoicesFor(item).map { it.kind }
        assertTrue(HideKind.LIKE_THIS !in kinds)
    }

    @Test
    fun anAlarmAndATaskHaveNothingToHideBy() {
        assertNull(StreamHideItems.itemFor(event(NOW, null, kind = EventKind.ALARM)))
        assertNull(StreamHideItems.itemFor(TaskContent(StreamContentId("t"), "Buy milk")))
    }
}

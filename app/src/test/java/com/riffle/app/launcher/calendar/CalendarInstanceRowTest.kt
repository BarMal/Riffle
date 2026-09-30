package com.riffle.app.launcher.calendar

import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.sources.CalendarItemMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CalendarInstanceRowTest {
    private val utc = ZoneId.of("UTC")
    private val auckland = ZoneId.of("Pacific/Auckland")

    private fun row(
        eventId: Long = 7L,
        begin: Long = 1_000L,
        end: Long = 2_000L,
        allDay: Boolean = false,
        accessLevel: Int = 0,
        title: String? = "Standup",
    ) = CalendarInstanceRow(eventId, title, begin, end, location = "Room 1", allDay = allDay, accessLevel = accessLevel)

    @Test
    fun recurringInstancesGetDistinctIdsButShareTheEventId() {
        val first = row(begin = 1_000L).toCalendarEvent(utc)!!
        val second = row(begin = 5_000L, end = 6_000L).toCalendarEvent(utc)!!

        assertEquals("7:1000", first.id)
        assertEquals("7:5000", second.id)
        assertEquals("7", first.eventId)
        assertEquals("7", second.eventId)
    }

    @Test
    fun privateAndConfidentialEventsAreSensitiveOthersAreVisible() {
        val privacy =
            listOf(0, 1, 2, 3).map { level ->
                val event = row(accessLevel = level).toCalendarEvent(utc)!!
                CalendarItemMapper().nextEvents(listOf(event), nowEpochMillis = 0).single().privacy
            }

        assertEquals(
            listOf(ItemPrivacy.VISIBLE, ItemPrivacy.SENSITIVE, ItemPrivacy.SENSITIVE, ItemPrivacy.VISIBLE),
            privacy,
        )
    }

    @Test
    fun allDayEventsMoveFromUtcMidnightToLocalMidnight() {
        val utcMidnight = 1_700_000_000_000L - (1_700_000_000_000L % DAY)
        val event = row(begin = utcMidnight, end = utcMidnight + DAY, allDay = true).toCalendarEvent(auckland)!!

        assertTrue(event.allDay)
        // 14 November 2023 is New Zealand daylight time (UTC+13).
        assertEquals(utcMidnight - 13 * HOUR, event.startEpochMillis)
        assertEquals(utcMidnight + DAY - 13 * HOUR, event.endEpochMillis)
    }

    @Test
    fun timedEventsKeepTheirInstants() {
        val event = row(begin = 1_000L, end = 2_000L).toCalendarEvent(auckland)!!

        assertEquals(1_000L, event.startEpochMillis)
        assertEquals(2_000L, event.endEpochMillis)
        assertFalse(event.allDay)
    }

    @Test
    fun missingTitleBecomesEmptyAndBackwardsEndsAreClamped() {
        val event = row(title = null, begin = 2_000L, end = 1_000L).toCalendarEvent(utc)!!

        assertEquals("", event.title)
        assertEquals(2_000L, event.endEpochMillis)
    }

    @Test
    fun unusableRowsAreDropped() {
        assertNull(row(begin = -1L).toCalendarEvent(utc))
        assertNull(row(eventId = -1L).toCalendarEvent(utc))
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

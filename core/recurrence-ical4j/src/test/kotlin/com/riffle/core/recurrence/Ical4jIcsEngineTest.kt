package com.riffle.core.recurrence

import com.riffle.core.domain.launcher.workspace.sources.ics.DefaultIcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsExpansion
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsParseResult
import com.riffle.core.domain.launcher.workspace.sources.ics.TimeWindow
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A feed text through the real parser and the ical4j-backed expander: the path the app takes. */
class Ical4jIcsEngineTest {
    private val engine = DefaultIcsEngine(Ical4jRecurrenceExpander())
    private val london = ZoneId.of("Europe/London")

    private val feed =
        listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "BEGIN:VEVENT",
            "UID:weekly@example.com",
            "SUMMARY:Standup",
            "DTSTART;TZID=Europe/London:20260323T090000",
            "DTEND;TZID=Europe/London:20260323T093000",
            "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=8",
            "EXDATE;TZID=Europe/London:20260413T090000",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "UID:weekly@example.com",
            "RECURRENCE-ID;TZID=Europe/London:20260406T090000",
            "SUMMARY:Standup (late)",
            "DTSTART;TZID=Europe/London:20260406T140000",
            "DTEND;TZID=Europe/London:20260406T143000",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "UID:holiday@example.com",
            "SUMMARY:Holiday",
            "DTSTART;VALUE=DATE:20260410",
            "DTEND;VALUE=DATE:20260412",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")

    @Test
    fun `weekly rule with exdate and override across the march clock change`() {
        val parsed = assertIs<IcsParseResult.Ok>(engine.parse(feed, london))
        val window = TimeWindow(Instant.parse("2026-03-23T00:00:00Z"), Instant.parse("2026-04-20T00:00:00Z"))
        val result = engine.expand(parsed.events, IcsExpansion(window, london))
        // Mondays: 23 Mar (GMT); 30 Mar (BST began 29 Mar, so 09:00 is 08:00Z);
        // 6 Apr moved to 14:00 BST; 13 Apr excluded.
        val standups = result.filter { it.uid == "weekly@example.com" }
        assertEquals(
            listOf("2026-03-23T09:00:00Z", "2026-03-30T08:00:00Z", "2026-04-06T13:00:00Z"),
            standups.map { Instant.ofEpochMilli(it.startEpochMillis).toString() },
        )
        assertEquals("Standup (late)", standups.last().title)
        val holiday = result.single { it.uid == "holiday@example.com" }
        assertEquals(true, holiday.allDay)
        assertEquals(2 * 24 * 3_600_000L, holiday.endEpochMillis - holiday.startEpochMillis)
    }
}

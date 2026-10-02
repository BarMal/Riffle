package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsParserTest {
    private val device = ZoneId.of("Europe/London")

    private fun calendar(vararg body: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + body + "END:VCALENDAR").joinToString("\r\n")

    private fun event(vararg lines: String) = listOf("BEGIN:VEVENT") + lines + "END:VEVENT"

    private fun parse(text: String): IcsParseResult.Ok = assertIs<IcsParseResult.Ok>(IcsParser.parse(text, device))

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `reads several events with their basic fields`() {
        val result =
            parse(
                calendar(
                    *event(
                        "UID:a",
                        "SUMMARY:Standup",
                        "LOCATION:Room 4",
                        "DTSTART:20260105T090000Z",
                        "DTEND:20260105T093000Z",
                    ).toTypedArray(),
                    *event("UID:b", "SUMMARY:Lunch", "DTSTART:20260106T120000Z", "DURATION:PT1H").toTypedArray(),
                ),
            )
        assertEquals(listOf("a", "b"), result.events.map { it.uid })
        val first = result.events[0]
        assertEquals("Standup", first.title)
        assertEquals("Room 4", first.location)
        assertEquals(at("2026-01-05T09:00"), first.start)
        assertEquals(ZoneOffset.UTC, first.zone)
        assertEquals(Duration.ofMinutes(30), first.duration)
        assertEquals(Duration.ofHours(1), result.events[1].duration)
        assertFalse(first.isRecurring)
    }

    @Test
    fun `tzid names an iana zone and a different end zone is converted`() {
        val result =
            parse(
                calendar(
                    *event(
                        "UID:t",
                        "SUMMARY:Flight",
                        "DTSTART;TZID=America/New_York:20260105T090000",
                        "DTEND;TZID=Europe/London:20260105T210000",
                    ).toTypedArray(),
                ),
            )
        val flight = result.events.single()
        assertEquals(ZoneId.of("America/New_York"), flight.zone)
        assertEquals(at("2026-01-05T09:00"), flight.start)
        assertEquals(Duration.ofHours(7), flight.duration)
    }

    @Test
    fun `windows and prefixed zone names are mapped and unknown ones become floating`() {
        val result =
            parse(
                calendar(
                    *event("UID:w", "DTSTART;TZID=Eastern Standard Time:20260105T090000").toTypedArray(),
                    *event(
                        "UID:p",
                        "DTSTART;TZID=/mozilla.org/20070129_1/Europe/Berlin:20260105T090000",
                    ).toTypedArray(),
                    *event("UID:u", "DTSTART;TZID=Nowhere/Land:20260105T090000").toTypedArray(),
                ),
            )
        assertEquals(ZoneId.of("America/New_York"), result.events[0].zone)
        assertEquals(ZoneId.of("Europe/Berlin"), result.events[1].zone)
        assertNull(result.events[2].zone)
        assertEquals(1, result.unknownZoneEvents)
    }

    @Test
    fun `x-wr-timezone gives floating events a zone`() {
        val result =
            parse(
                calendar("X-WR-TIMEZONE:Asia/Tokyo", *event("UID:f", "DTSTART:20260105T090000").toTypedArray()),
            )
        assertEquals(ZoneId.of("Asia/Tokyo"), result.events.single().zone)
    }

    @Test
    fun `all day events use whole days and an exclusive end`() {
        val result =
            parse(
                calendar(
                    *event("UID:d1", "DTSTART;VALUE=DATE:20260301", "DTEND;VALUE=DATE:20260304").toTypedArray(),
                    *event("UID:d2", "DTSTART;VALUE=DATE:20260310").toTypedArray(),
                ),
            )
        val trip = result.events[0]
        assertTrue(trip.allDay)
        assertNull(trip.zone)
        assertEquals(at("2026-03-01T00:00"), trip.start)
        assertEquals(Duration.ofDays(3), trip.duration)
        assertEquals(Duration.ofDays(1), result.events[1].duration)
    }

    @Test
    fun `recurrence rule exdate rdate and an override attach to the master`() {
        val result =
            parse(
                calendar(
                    *event(
                        "UID:r",
                        "SUMMARY:Weekly",
                        "DTSTART;TZID=Europe/London:20260105T090000",
                        "DTEND;TZID=Europe/London:20260105T100000",
                        "RRULE:FREQ=WEEKLY;COUNT=10",
                        "EXDATE;TZID=Europe/London:20260112T090000",
                        "EXDATE:20260126T090000Z,20260202T090000Z",
                        "RDATE;TZID=Europe/London:20260115T150000",
                    ).toTypedArray(),
                    *event(
                        "UID:r",
                        "SUMMARY:Weekly (moved)",
                        "RECURRENCE-ID;TZID=Europe/London:20260119T090000",
                        "DTSTART;TZID=Europe/London:20260119T130000",
                        "DTEND;TZID=Europe/London:20260119T143000",
                        "LOCATION:Annex",
                    ).toTypedArray(),
                    *event(
                        "UID:r",
                        "RECURRENCE-ID;TZID=Europe/London:20260209T090000",
                        "DTSTART;TZID=Europe/London:20260209T090000",
                        "STATUS:CANCELLED",
                    ).toTypedArray(),
                ),
            )
        val weekly = result.events.single()
        assertEquals("FREQ=WEEKLY;COUNT=10", weekly.rrule)
        assertEquals(setOf(at("2026-01-12T09:00"), at("2026-01-26T09:00"), at("2026-02-02T09:00")), weekly.exDates)
        assertEquals(setOf(at("2026-01-15T15:00")), weekly.rDates)
        val moved = weekly.overrides.first { it.recurrenceId == at("2026-01-19T09:00") }
        assertEquals(at("2026-01-19T13:00"), moved.start)
        assertEquals(Duration.ofMinutes(90), moved.duration)
        assertEquals("Weekly (moved)", moved.title)
        assertEquals("Annex", moved.location)
        assertTrue(weekly.overrides.first { it.recurrenceId == at("2026-02-09T09:00") }.cancelled)
        assertTrue(weekly.isRecurring)
    }

    @Test
    fun `an override without a master stands alone and a cancelled master hides everything`() {
        val result =
            parse(
                calendar(
                    *event("UID:o", "SUMMARY:Invited", "RECURRENCE-ID:20260105T090000Z", "DTSTART:20260105T100000Z")
                        .toTypedArray(),
                    *event("UID:x", "DTSTART:20260105T090000Z", "RRULE:FREQ=DAILY", "STATUS:CANCELLED").toTypedArray(),
                    *event("UID:x", "RECURRENCE-ID:20260106T090000Z", "DTSTART:20260106T090000Z").toTypedArray(),
                ),
            )
        val only = result.events.single()
        assertEquals("o", only.uid)
        assertEquals(at("2026-01-05T10:00"), only.start)
        assertNull(only.rrule)
    }

    @Test
    fun `private and confidential classes are private`() {
        val result =
            parse(
                calendar(
                    *event("UID:1", "DTSTART:20260105T090000Z", "CLASS:PRIVATE").toTypedArray(),
                    *event("UID:2", "DTSTART:20260105T090000Z", "CLASS:confidential").toTypedArray(),
                    *event("UID:3", "DTSTART:20260105T090000Z", "CLASS:PUBLIC").toTypedArray(),
                ),
            )
        assertEquals(listOf(true, true, false), result.events.map { it.isPrivate })
    }

    @Test
    fun `folded lines and escapes are decoded`() {
        val result =
            parse(
                "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:e\r\nDTSTART:20260105T090000Z\r\n" +
                    "SUMMARY:Planning\\, part\r\n  one\\; with\\nbreak\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
            )
        assertEquals("Planning, part one; with break", result.events.single().title)
    }

    @Test
    fun `quoted parameters may contain colons and semicolons`() {
        val result =
            parse(calendar(*event("UID:q", "DTSTART;X-NOTE=\"a:b;c\";TZID=Asia/Tokyo:20260105T090000").toTypedArray()))
        assertEquals(ZoneId.of("Asia/Tokyo"), result.events.single().zone)
    }

    @Test
    fun `nested alarms and other components do not leak into the event`() {
        val result =
            parse(
                calendar(
                    "BEGIN:VTIMEZONE",
                    "TZID:Custom",
                    "BEGIN:STANDARD",
                    "DTSTART:19700101T000000",
                    "END:STANDARD",
                    "END:VTIMEZONE",
                    "BEGIN:VEVENT",
                    "UID:n",
                    "SUMMARY:Real",
                    "DTSTART:20260105T090000Z",
                    "BEGIN:VALARM",
                    "SUMMARY:alarm text",
                    "DTSTART:20990101T000000Z",
                    "END:VALARM",
                    "END:VEVENT",
                    "BEGIN:VTODO",
                    "UID:todo",
                    "DTSTART:20260105T090000Z",
                    "END:VTODO",
                ),
            )
        val only = result.events.single()
        assertEquals("Real", only.title)
        assertEquals(at("2026-01-05T09:00"), only.start)
    }

    @Test
    fun `missing uid gets a stable generated one`() {
        val text = calendar(*event("SUMMARY:No id", "DTSTART:20260105T090000Z").toTypedArray())
        assertEquals(parse(text).events.single().uid, parse(text).events.single().uid)
        assertTrue(parse(text).events.single().uid.startsWith("no-uid-"))
    }

    @Test
    fun `events without a usable start are skipped and counted`() {
        val result =
            parse(
                calendar(
                    *event("UID:none", "SUMMARY:No start").toTypedArray(),
                    *event("UID:bad", "DTSTART:2026-01-05").toTypedArray(),
                    *event("UID:ok", "DTSTART:20260105T090000Z").toTypedArray(),
                ),
            )
        assertEquals(listOf("ok"), result.events.map { it.uid })
        assertEquals(2, result.skippedEvents)
    }

    @Test
    fun `a thisandfuture override is refused rather than misapplied`() {
        val result =
            parse(
                calendar(
                    *event("UID:m", "DTSTART:20260105T090000Z", "RRULE:FREQ=DAILY").toTypedArray(),
                    *event("UID:m", "RECURRENCE-ID;RANGE=THISANDFUTURE:20260107T090000Z", "DTSTART:20260107T100000Z")
                        .toTypedArray(),
                ),
            )
        assertTrue(result.events.single().overrides.isEmpty())
        assertEquals(1, result.skippedEvents)
    }

    @Test
    fun `a duration longer than a year is capped and a negative one is zero`() {
        val result =
            parse(
                calendar(
                    *event("UID:l", "DTSTART:20260105T090000Z", "DTEND:21990105T090000Z").toTypedArray(),
                    *event("UID:n", "DTSTART:20260105T090000Z", "DTEND:20260101T090000Z").toTypedArray(),
                ),
            )
        assertEquals(Duration.ofDays(IcsLimits.MAX_DURATION_DAYS), result.events[0].duration)
        assertEquals(Duration.ZERO, result.events[1].duration)
    }

    @Test
    fun `text that is not a calendar fails without echoing it`() {
        val failed = assertIs<IcsParseResult.Failed>(IcsParser.parse("<html>secret-token</html>", device))
        assertEquals(IcsParseFailure.NOT_CALENDAR, failed.reason)
        assertFalse(failed.toString().contains("secret"))
    }
}

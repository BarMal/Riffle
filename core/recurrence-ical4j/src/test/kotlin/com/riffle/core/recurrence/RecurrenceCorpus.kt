package com.riffle.core.recurrence

import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceExpander
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceFailure
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceOverride
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceRequest
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceResult
import com.riffle.core.domain.launcher.workspace.sources.ics.TimeWindow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RRULE acceptance corpus. Expectations are taken from the RFC 5545 section 3.8.5.3 examples where one
 * exists (marked RFC) and otherwise worked out by hand from the calendar. It is written against
 * [RecurrenceExpander] only: a replacement implementation subclasses this and must pass the same table.
 */
abstract class RecurrenceCorpus {
    protected abstract fun newExpander(): RecurrenceExpander

    private val expander: RecurrenceExpander by lazy { newExpander() }

    @Suppress("LongParameterList")
    private fun expand(
        rrule: String?,
        start: String,
        zone: String = NEW_YORK,
        allDay: Boolean = false,
        window: Pair<String, String> = "1990-01-01T00:00:00Z" to "2045-01-01T00:00:00Z",
        exDates: List<String> = emptyList(),
        rDates: List<String> = emptyList(),
        overrides: List<RecurrenceOverride> = emptyList(),
    ): RecurrenceResult =
        expander.expand(
            RecurrenceRequest(
                rrule = rrule,
                dtStart = LocalDateTime.parse(start),
                allDay = allDay,
                zone = ZoneId.of(zone),
                exDates = exDates.map(LocalDateTime::parse).toSet(),
                rDates = rDates.map(LocalDateTime::parse).toSet(),
                overrides = overrides,
                window = TimeWindow(Instant.parse(window.first), Instant.parse(window.second)),
            ),
        )

    private fun ok(result: RecurrenceResult): RecurrenceResult.Ok {
        assertTrue(result is RecurrenceResult.Ok, "expected Ok but was $result")
        return result
    }

    private fun locals(result: RecurrenceResult): List<String> =
        ok(
            result,
        ).occurrences.map { it.originalStart.toString() }

    private fun instants(result: RecurrenceResult): List<String> = ok(result).occurrences.map { it.start.toString() }

    private fun assertLocals(
        expected: List<String>,
        result: RecurrenceResult,
    ) = assertEquals(expected, locals(result))

    // ---- daily / weekly ----------------------------------------------------------------------------

    @Test
    fun `daily with count`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00", "2025-01-08T09:00"),
            expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00"),
        )

    @Test
    fun `daily every other day until a UTC instant is converted to the event zone`() =
        // 2025-01-12T00:00Z is 2025-01-11 19:00 in New York, so the 12th (09:00) is past it; the 10th is not.
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-08T09:00", "2025-01-10T09:00"),
            expand("FREQ=DAILY;INTERVAL=2;UNTIL=20250112T000000Z", "2025-01-06T09:00"),
        )

    @Test
    fun `date-only until is inclusive of the whole day`() =
        assertLocals(
            (6..10).map { "2025-01-%02dT09:00".format(it) },
            expand("FREQ=DAILY;UNTIL=20250110", "2025-01-06T09:00"),
        )

    @Test
    fun `until exactly at an occurrence includes it`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00"),
            expand("FREQ=DAILY;UNTIL=20250107T090000", "2025-01-06T09:00"),
        )

    @Test
    fun `weekly on several weekdays`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-08T09:00", "2025-01-10T09:00", "2025-01-13T09:00", "2025-01-15T09:00"),
            expand("FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=5", "2025-01-06T09:00"),
        )

    @Test
    fun `weekly every other week`() =
        assertLocals(
            listOf("2025-01-07T09:00", "2025-01-09T09:00", "2025-01-21T09:00", "2025-01-23T09:00"),
            expand("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=4", "2025-01-07T09:00"),
        )

    @Test
    fun `RFC week start Monday`() =
        assertLocals(
            listOf("1997-08-05T09:00", "1997-08-10T09:00", "1997-08-19T09:00", "1997-08-24T09:00"),
            expand("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO", "1997-08-05T09:00"),
        )

    @Test
    fun `RFC week start Sunday changes the result`() =
        assertLocals(
            listOf("1997-08-05T09:00", "1997-08-17T09:00", "1997-08-19T09:00", "1997-08-31T09:00"),
            expand("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU", "1997-08-05T09:00"),
        )

    // ---- monthly -----------------------------------------------------------------------------------

    @Test
    fun `monthly on a day of the month`() =
        assertLocals(
            listOf("2025-01-15T09:00", "2025-02-15T09:00", "2025-03-15T09:00"),
            expand("FREQ=MONTHLY;BYMONTHDAY=15;COUNT=3", "2025-01-15T09:00"),
        )

    @Test
    fun `monthly on the 31st skips months without one`() =
        assertLocals(
            listOf("2025-01-31T09:00", "2025-03-31T09:00", "2025-05-31T09:00", "2025-07-31T09:00"),
            expand("FREQ=MONTHLY;BYMONTHDAY=31;COUNT=4", "2025-01-31T09:00"),
        )

    @Test
    fun `monthly on the last day`() =
        assertLocals(
            listOf("2025-01-31T09:00", "2025-02-28T09:00", "2025-03-31T09:00"),
            expand("FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3", "2025-01-31T09:00"),
        )

    @Test
    fun `monthly second Tuesday`() =
        assertLocals(
            listOf("2025-01-14T09:00", "2025-02-11T09:00", "2025-03-11T09:00"),
            expand("FREQ=MONTHLY;BYDAY=2TU;COUNT=3", "2025-01-14T09:00"),
        )

    @Test
    fun `monthly last Friday`() =
        assertLocals(
            listOf("2025-01-31T09:00", "2025-02-28T09:00", "2025-03-28T09:00"),
            expand("FREQ=MONTHLY;BYDAY=-1FR;COUNT=3", "2025-01-31T09:00"),
        )

    @Test
    fun `RFC last weekday of the month with BYSETPOS`() =
        assertLocals(
            listOf("2025-01-31T09:00", "2025-02-28T09:00", "2025-03-31T09:00"),
            expand("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=3", "2025-01-31T09:00"),
        )

    @Test
    fun `RFC second to last weekday of the month with BYSETPOS`() =
        assertLocals(
            listOf("2025-01-30T09:00", "2025-02-27T09:00", "2025-03-28T09:00"),
            expand("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2;COUNT=3", "2025-01-30T09:00"),
        )

    @Test
    fun `monthly first and last weekday with several BYSETPOS`() =
        assertLocals(
            listOf("2025-01-31T09:00", "2025-02-03T09:00", "2025-02-28T09:00"),
            expand("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1,-1;COUNT=3", "2025-01-31T09:00"),
        )

    // ---- yearly ------------------------------------------------------------------------------------

    @Test
    fun `yearly with count`() =
        assertLocals(
            listOf("2025-03-10T09:00", "2026-03-10T09:00", "2027-03-10T09:00"),
            expand("FREQ=YEARLY;COUNT=3", "2025-03-10T09:00"),
        )

    @Test
    fun `RFC US presidential election day`() =
        assertLocals(
            listOf("1996-11-05T09:00", "2000-11-07T09:00", "2004-11-02T09:00"),
            expand(
                "FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8;COUNT=3",
                "1996-11-05T09:00",
            ),
        )

    @Test
    fun `RFC Monday of week 20`() =
        assertLocals(
            listOf("1997-05-12T09:00", "1998-05-11T09:00", "1999-05-17T09:00"),
            expand("FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO;COUNT=3", "1997-05-12T09:00"),
        )

    @Test
    fun `RFC by year day`() =
        assertLocals(
            listOf(
                "1997-01-01T09:00",
                "1997-04-10T09:00",
                "1997-07-19T09:00",
                "2000-01-01T09:00",
                "2000-04-09T09:00",
                "2000-07-18T09:00",
                "2003-01-01T09:00",
                "2003-04-10T09:00",
                "2003-07-19T09:00",
                "2006-01-01T09:00",
            ),
            expand("FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200", "1997-01-01T09:00"),
        )

    @Test
    fun `leap day yearly only occurs in leap years`() =
        assertLocals(
            listOf("2024-02-29T09:00", "2028-02-29T09:00", "2032-02-29T09:00"),
            expand("FREQ=YEARLY;COUNT=3", "2024-02-29T09:00"),
        )

    @Test
    fun `leap day by month and day keeps the leap day`() =
        assertLocals(
            listOf("2024-02-29T09:00", "2028-02-29T09:00"),
            expand("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29;COUNT=2", "2024-02-29T09:00"),
        )

    // ---- exdate, rdate, overrides --------------------------------------------------------------------

    @Test
    fun `exdate removes one instance`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-08T09:00"),
            expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00", exDates = listOf("2025-01-07T09:00")),
        )

    @Test
    fun `exdate of the first instance removes it`() =
        assertLocals(
            listOf("2025-01-07T09:00", "2025-01-08T09:00"),
            expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00", exDates = listOf("2025-01-06T09:00")),
        )

    @Test
    fun `exdate that matches no instance changes nothing`() =
        assertEquals(
            3,
            ok(expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00", exDates = listOf("2025-01-06T10:00"))).occurrences.size,
        )

    @Test
    fun `exdate does not count against COUNT`() =
        // RFC 5545: COUNT is applied to the rule before EXDATE, so removing one leaves COUNT-1 instances.
        assertEquals(
            2,
            ok(expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00", exDates = listOf("2025-01-07T09:00"))).occurrences.size,
        )

    @Test
    fun `rdate adds an instance and merges with the rule`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00", "2025-01-07T15:00", "2025-01-08T09:00"),
            expand("FREQ=DAILY;COUNT=3", "2025-01-06T09:00", rDates = listOf("2025-01-07T15:00")),
        )

    @Test
    fun `rdate equal to a rule instance is not duplicated`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00"),
            expand("FREQ=DAILY;COUNT=2", "2025-01-06T09:00", rDates = listOf("2025-01-07T09:00")),
        )

    @Test
    fun `exdate also removes an rdate`() =
        assertLocals(
            listOf("2025-01-06T09:00"),
            expand(
                "FREQ=DAILY;COUNT=1",
                "2025-01-06T09:00",
                rDates = listOf("2025-01-07T09:00"),
                exDates = listOf("2025-01-07T09:00"),
            ),
        )

    @Test
    fun `rdate only event with no rule`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-02-01T09:00"),
            expand(null, "2025-01-06T09:00", rDates = listOf("2025-02-01T09:00")),
        )

    @Test
    fun `single event without rule or rdates`() =
        assertLocals(
            listOf("2025-01-06T09:00"),
            expand(null, "2025-01-06T09:00"),
        )

    @Test
    fun `moved instance replaces the original and keeps its recurrence id`() {
        val result =
            ok(
                expand(
                    "FREQ=DAILY;COUNT=3",
                    "2025-01-06T09:00",
                    overrides =
                        listOf(
                            RecurrenceOverride(
                                recurrenceId = LocalDateTime.parse("2025-01-07T09:00"),
                                replacementStart = LocalDateTime.parse("2025-01-09T14:00"),
                            ),
                        ),
                ),
            )
        assertEquals(
            listOf("2025-01-06T14:00:00Z", "2025-01-08T14:00:00Z", "2025-01-09T19:00:00Z"),
            result.occurrences.map { it.start.toString() },
        )
        assertEquals(LocalDateTime.parse("2025-01-07T09:00"), result.occurrences.last().originalStart)
    }

    @Test
    fun `cancelled instance is dropped`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-08T09:00"),
            expand(
                "FREQ=DAILY;COUNT=3",
                "2025-01-06T09:00",
                overrides = listOf(RecurrenceOverride(LocalDateTime.parse("2025-01-07T09:00"), null)),
            ),
        )

    @Test
    fun `instance moved out of the window is not reported and one moved in is`() {
        val moved =
            listOf(
                RecurrenceOverride(LocalDateTime.parse("2025-01-07T09:00"), LocalDateTime.parse("2025-03-01T09:00")),
                RecurrenceOverride(LocalDateTime.parse("2025-01-20T09:00"), LocalDateTime.parse("2025-01-08T18:00")),
            )
        val result =
            expand(
                "FREQ=DAILY;COUNT=3",
                "2025-01-06T09:00",
                window = "2025-01-06T00:00:00Z" to "2025-01-09T00:00:00Z",
                overrides = moved,
            )
        assertEquals(listOf("2025-01-06T14:00:00Z", "2025-01-08T14:00:00Z", "2025-01-08T23:00:00Z"), instants(result))
    }

    // ---- time zones, DST, all-day --------------------------------------------------------------------

    @Test
    fun `daily wall clock time is stable across spring forward`() =
        assertEquals(
            listOf("2025-03-08T14:00:00Z", "2025-03-09T13:00:00Z", "2025-03-10T13:00:00Z"),
            instants(expand("FREQ=DAILY;COUNT=3", "2025-03-08T09:00")),
        )

    @Test
    fun `a time skipped by spring forward moves forward by the gap`() {
        val result = expand("FREQ=DAILY;COUNT=3", "2025-03-08T02:30")
        assertEquals(
            listOf("2025-03-08T07:30:00Z", "2025-03-09T07:30:00Z", "2025-03-10T06:30:00Z"),
            instants(result),
        )
        // The skipped local time is still the instance's recurrence id.
        assertEquals("2025-03-09T02:30", ok(result).occurrences[1].originalStart.toString())
    }

    @Test
    fun `an ambiguous time on fall back is the first occurrence`() =
        assertEquals(
            listOf("2025-11-01T05:30:00Z", "2025-11-02T05:30:00Z", "2025-11-03T06:30:00Z"),
            instants(expand("FREQ=DAILY;COUNT=3", "2025-11-01T01:30")),
        )

    @Test
    fun `a zone that abolished daylight saving`() =
        // Sao Paulo ended DST at 2019-02-17 00:00 (clocks back to 2019-02-16 23:00), UTC-2 to UTC-3.
        assertEquals(
            listOf("2019-02-15T11:00:00Z", "2019-02-16T11:00:00Z", "2019-02-17T12:00:00Z", "2019-02-18T12:00:00Z"),
            instants(expand("FREQ=DAILY;COUNT=4", "2019-02-15T09:00", zone = "America/Sao_Paulo")),
        )

    @Test
    fun `event zone differs from the window zone`() =
        // Tokyo is UTC+9 all year, so 08:00 local is 23:00Z the previous day.
        assertEquals(
            listOf("2025-01-05T23:00:00Z", "2025-01-12T23:00:00Z"),
            instants(expand("FREQ=WEEKLY;COUNT=2", "2025-01-06T08:00", zone = "Asia/Tokyo")),
        )

    @Test
    fun `UTC event zone has no offset`() =
        assertEquals(
            listOf("2025-07-01T09:00:00Z", "2025-07-02T09:00:00Z"),
            instants(expand("FREQ=DAILY;COUNT=2", "2025-07-01T09:00", zone = "UTC")),
        )

    @Test
    fun `all-day events start at local midnight and follow the offset`() =
        assertEquals(
            listOf("2025-03-08T05:00:00Z", "2025-03-09T05:00:00Z", "2025-03-10T04:00:00Z"),
            instants(expand("FREQ=DAILY;COUNT=3", "2025-03-08T00:00", allDay = true)),
        )

    @Test
    fun `all-day weekly with exdate`() =
        assertLocals(
            listOf("2025-01-06T00:00", "2025-01-20T00:00"),
            expand(
                "FREQ=WEEKLY;COUNT=3",
                "2025-01-06T00:00",
                allDay = true,
                exDates = listOf("2025-01-13T00:00"),
            ),
        )

    // ---- window, cap, failure ------------------------------------------------------------------------

    private fun capped(
        rrule: String,
        max: Int,
    ): RecurrenceResult.Ok =
        ok(
            expander.expand(
                RecurrenceRequest(
                    rrule = rrule,
                    dtStart = LocalDateTime.parse("2025-01-01T09:00"),
                    allDay = false,
                    zone = ZoneId.of(NEW_YORK),
                    window = TimeWindow(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
                    maxInstances = max,
                ),
            ),
        )

    @Test
    fun `an infinite rule stops at the instance cap and says so`() {
        val result = capped("FREQ=DAILY", max = 10)
        assertEquals(10, result.occurrences.size)
        assertTrue(result.truncated)
        assertEquals("2025-01-10T09:00", result.occurrences.last().originalStart.toString())
    }

    @Test
    fun `a rule that exactly fits the cap is not truncated`() {
        val result = capped("FREQ=DAILY;COUNT=10", max = 10)
        assertEquals(10, result.occurrences.size)
        assertEquals(false, result.truncated)
    }

    @Test
    fun `a rule that generates thousands of starts per day is still capped`() {
        val result =
            capped(
                "FREQ=DAILY;BYHOUR=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23;BYMINUTE=0,30",
                max = 50,
            )
        assertEquals(50, result.occurrences.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `a window far after the start still gets the right instances`() =
        assertLocals(
            (1..7).map { "2025-06-%02dT09:00".format(it) },
            expand("FREQ=DAILY", "2020-01-01T09:00", window = "2025-06-01T00:00:00Z" to "2025-06-08T00:00:00Z"),
        )

    @Test
    fun `count is counted from the start not from the window`() =
        assertLocals(
            listOf("2025-01-08T09:00", "2025-01-09T09:00", "2025-01-10T09:00"),
            expand(
                "FREQ=DAILY;COUNT=10",
                "2025-01-01T09:00",
                window = "2025-01-08T00:00:00Z" to "2025-02-01T00:00:00Z",
            ),
        )

    @Test
    fun `window is half open`() =
        assertLocals(
            listOf("2025-01-02T09:00"),
            // 09:00 New York is 14:00Z: the instance at the window start is in, the one at its end is out.
            expand("FREQ=DAILY;COUNT=3", "2025-01-01T09:00", window = "2025-01-02T14:00:00Z" to "2025-01-03T14:00:00Z"),
        )

    @Test
    fun `a huge count with a small window returns promptly`() {
        val result =
            ok(
                expand(
                    "FREQ=DAILY;COUNT=2147483647",
                    "1970-01-01T09:00",
                    window = "2025-06-01T00:00:00Z" to "2025-06-08T00:00:00Z",
                ),
            )
        assertEquals(7, result.occurrences.size)
        assertEquals(false, result.truncated)
    }

    @Test
    fun `failures carry a code and never throw`() {
        val invalid =
            listOf(
                "garbage",
                "FREQ=BOGUS",
                "COUNT=3",
                "FREQ=DAILY;COUNT=abc",
                "FREQ=DAILY;UNTIL=nonsense",
                "FREQ=DAILY;COUNT=1;COUNT=2",
                "FREQ=DAILY;BYDAY=XX",
                "FREQ=DAILY;BYMONTHDAY=99",
                "FREQ=" + "DAILY;X=".padEnd(2000, 'A'),
            )
        for (rule in invalid) {
            assertEquals(
                RecurrenceResult.Failed(RecurrenceFailure.INVALID_RULE),
                expand(rule, "2025-01-06T09:00"),
                "rule: $rule",
            )
        }
    }

    @Test
    fun `refused rules are unsupported not invalid`() {
        for (rule in listOf("FREQ=HOURLY", "FREQ=MINUTELY;COUNT=5", "FREQ=SECONDLY", "FREQ=DAILY;RSCALE=HEBREW")) {
            assertEquals(
                RecurrenceResult.Failed(RecurrenceFailure.UNSUPPORTED_RULE),
                expand(rule, "2025-01-06T09:00"),
                "rule: $rule",
            )
        }
    }

    @Test
    fun `lower case rule text and a Gregorian RSCALE are accepted`() =
        assertLocals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00"),
            expand("freq=daily;count=2;rscale=gregorian", "2025-01-06T09:00"),
        )

    private companion object {
        const val NEW_YORK = "America/New_York"
    }
}

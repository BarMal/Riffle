package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Untrusted feed text: nothing may throw, hang or exceed the documented bounds. */
class IcsParserHostileInputTest {
    private val device = ZoneId.of("UTC")
    private val start = "DTSTART:20260105T090000Z"

    private fun parse(text: String) = IcsParser.parse(text, device)

    private fun wrap(vararg lines: String) =
        (listOf("BEGIN:VCALENDAR", "BEGIN:VEVENT") + lines + listOf("END:VEVENT", "END:VCALENDAR")).joinToString("\r\n")

    @Test
    fun `empty and whitespace input is not a calendar`() {
        assertEquals(IcsParseFailure.NOT_CALENDAR, assertIs<IcsParseResult.Failed>(parse("")).reason)
        assertEquals(IcsParseFailure.NOT_CALENDAR, assertIs<IcsParseResult.Failed>(parse(" \r\n\t\n")).reason)
    }

    @Test
    fun `input over the size limit is refused before parsing`() {
        val big = "BEGIN:VCALENDAR\r\n" + "X-PAD:" + "a".repeat(IcsLimits.MAX_INPUT_CHARS)
        assertEquals(IcsParseFailure.TOO_LARGE, assertIs<IcsParseResult.Failed>(parse(big)).reason)
    }

    @Test
    fun `an oversize line is dropped and the rest still parses`() {
        val result =
            assertIs<IcsParseResult.Ok>(
                parse(wrap("UID:a", "SUMMARY:" + "x".repeat(IcsLimits.MAX_LINE_CHARS + 10), start)),
            )
        assertEquals("", result.events.single().title)
    }

    @Test
    fun `a folded line growing past the limit is dropped as a whole`() {
        val folded = (listOf("SUMMARY:start") + List(40) { " " + "y".repeat(500) }).joinToString("\r\n")
        val result = assertIs<IcsParseResult.Ok>(parse(wrap("UID:a", folded, "DTSTART:20260105T090000Z")))
        assertEquals("", result.events.single().title)
    }

    @Test
    fun `titles and locations are cut at the text limit`() {
        val result =
            assertIs<IcsParseResult.Ok>(
                parse(wrap("UID:a", "SUMMARY:" + "t".repeat(2000), "LOCATION:" + "l".repeat(2000), start)),
            )
        assertEquals(IcsLimits.MAX_TEXT_CHARS, result.events.single().title.length)
        assertEquals(IcsLimits.MAX_TEXT_CHARS, result.events.single().location?.length)
    }

    @Test
    fun `too many events truncate the list`() {
        val body =
            buildString {
                append("BEGIN:VCALENDAR\r\n")
                repeat(IcsLimits.MAX_EVENTS + 25) { i ->
                    append("BEGIN:VEVENT\r\nUID:e$i\r\nDTSTART:20260105T090000Z\r\nEND:VEVENT\r\n")
                }
                append("END:VCALENDAR\r\n")
            }
        val result = assertIs<IcsParseResult.Ok>(parse(body))
        assertEquals(IcsLimits.MAX_EVENTS, result.events.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `exdate and rdate lists are bounded`() {
        val dates =
            (0 until IcsLimits.MAX_EX_DATES + 500).joinToString(",") {
                "2026%02d%02dT090000Z".format(1 + it % 12, 1 + it % 28)
            }
        val unique = (0 until 5000).map { "%04d0101T090000Z".format(1971 + it % 200) }.joinToString(",")
        val result = assertIs<IcsParseResult.Ok>(parse(wrap("UID:a", start, "EXDATE:$dates$unique")))
        assertTrue(result.events.single().exDates.size <= IcsLimits.MAX_EX_DATES)
    }

    @Test
    fun `an overlong rule is dropped and the event stays single`() {
        val result =
            assertIs<IcsParseResult.Ok>(
                parse(wrap("UID:a", "DTSTART:20260105T090000Z", "RRULE:FREQ=DAILY;BYDAY=" + "MO,".repeat(600))),
            )
        assertEquals(null, result.events.single().rrule)
    }

    @Test
    fun `unbalanced and deeply nested components do not throw`() {
        val deep = "BEGIN:VCALENDAR\r\n" + "BEGIN:X\r\n".repeat(5000) + "BEGIN:VEVENT\r\nDTSTART:20260105T090000Z\r\n"
        assertIs<IcsParseResult.Ok>(parse(deep))
        assertIs<IcsParseResult.Ok>(parse("BEGIN:VCALENDAR\r\nEND:VEVENT\r\nEND:VEVENT\r\nEND:VCALENDAR"))
        assertIs<IcsParseResult.Ok>(parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:a"))
    }

    @Test
    fun `malformed values are skipped not thrown`() {
        val values =
            listOf(
                "20261340T250000Z",
                "99999999T999999",
                "20260230",
                "",
                "T",
                "20260105T0900",
                "２０２６０１０５",
                "-1-1-1",
                "0000",
            )
        values.forEach { value ->
            assertIs<IcsParseResult.Ok>(parse(wrap("UID:a", "DTSTART:$value")))
            assertIs<IcsParseResult.Ok>(parse(wrap("UID:a", start, "DTEND:$value", "EXDATE:$value")))
        }
        listOf("P", "PT", "P99999999999999999999D", "-P1D", "P1.5D", "PT1H1H", "garbage", "P1WT").forEach { value ->
            assertIs<IcsParseResult.Ok>(parse(wrap("UID:a", "DTSTART:20260105T090000Z", "DURATION:$value")))
        }
    }

    @Test
    fun `control characters and escapes collapse to plain text`() {
        val result =
            assertIs<IcsParseResult.Ok>(
                parse(wrap("UID:a", "SUMMARY:a\u0000b\u0007c", "DTSTART:20260105T090000Z")),
            )
        assertEquals("a b c", result.events.single().title)
    }

    @Test
    fun `random byte soup never throws`() {
        val random = Random(1409)
        val alphabet = "BEGINVCALDRTSUMYZ:;=\\\"\r\n \t,0123456789-+/"
        repeat(300) {
            val text = String(CharArray(random.nextInt(0, 600)) { alphabet[random.nextInt(alphabet.length)] })
            parse(text)
            parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n$text\r\nEND:VEVENT\r\nEND:VCALENDAR")
        }
    }

    @Test
    fun `mutated valid feeds never throw`() {
        val seed =
            wrap(
                "UID:a",
                "SUMMARY:Seed",
                "DTSTART;TZID=Europe/London:20260105T090000",
                "RRULE:FREQ=WEEKLY;COUNT=4",
                "EXDATE;TZID=Europe/London:20260112T090000",
            )
        val random = Random(7)
        repeat(500) {
            val chars = seed.toCharArray()
            repeat(1 + random.nextInt(8)) { chars[random.nextInt(chars.size)] = random.nextInt(32, 127).toChar() }
            parse(String(chars))
        }
    }
}

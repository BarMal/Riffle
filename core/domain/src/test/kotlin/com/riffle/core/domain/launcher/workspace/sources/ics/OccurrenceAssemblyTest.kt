package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The library-independent half of recurrence expansion, driven with hand-written rule output. */
class OccurrenceAssemblyTest {
    private val newYork = ZoneId.of("America/New_York")

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun request(
        start: String = "2025-01-06T09:00",
        allDay: Boolean = false,
        exDates: Set<LocalDateTime> = emptySet(),
        rDates: Set<LocalDateTime> = emptySet(),
        overrides: List<RecurrenceOverride> = emptyList(),
        window: TimeWindow = TimeWindow(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
        max: Int = 100,
    ) = RecurrenceRequest(
        rrule = null,
        dtStart = at(start),
        allDay = allDay,
        zone = newYork,
        exDates = exDates,
        rDates = rDates,
        overrides = overrides,
        window = window,
        maxInstances = max,
    )

    @Test
    fun `dtstart is always an occurrence even when the rule output omits it`() {
        val result = request().assemble(listOf(at("2025-01-07T09:00")))
        assertEquals(
            listOf(at("2025-01-06T09:00"), at("2025-01-07T09:00")),
            result.occurrences.map { it.originalStart },
        )
    }

    @Test
    fun `occurrences are ordered and unique`() {
        val result = request().assemble(listOf(at("2025-01-08T09:00"), at("2025-01-07T09:00"), at("2025-01-07T09:00")))
        assertEquals(
            listOf("2025-01-06T09:00", "2025-01-07T09:00", "2025-01-08T09:00"),
            result.occurrences.map { it.originalStart.toString() },
        )
    }

    @Test
    fun `exdate removes dtstart and rule output`() {
        val result =
            request(exDates = setOf(at("2025-01-06T09:00"), at("2025-01-07T09:00")))
                .assemble(listOf(at("2025-01-07T09:00"), at("2025-01-08T09:00")))
        assertEquals(listOf(at("2025-01-08T09:00")), result.occurrences.map { it.originalStart })
    }

    @Test
    fun `cancelled override removes the original and a moved override replaces it`() {
        val overrides =
            listOf(
                RecurrenceOverride(at("2025-01-07T09:00"), null),
                RecurrenceOverride(at("2025-01-08T09:00"), at("2025-01-08T11:00")),
            )
        val result = request(overrides = overrides).assemble(listOf(at("2025-01-07T09:00"), at("2025-01-08T09:00")))
        assertEquals(
            listOf("2025-01-06T14:00:00Z", "2025-01-08T16:00:00Z"),
            result.occurrences.map { it.start.toString() },
        )
        assertEquals(at("2025-01-08T09:00"), result.occurrences.last().originalStart)
    }

    @Test
    fun `an override whose original was exdated stays cancelled`() {
        val overrides = listOf(RecurrenceOverride(at("2025-01-07T09:00"), at("2025-01-07T12:00")))
        val result =
            request(
                exDates = setOf(at("2025-01-07T09:00")),
                overrides = overrides,
            ).assemble(listOf(at("2025-01-07T09:00")))
        assertEquals(listOf(at("2025-01-06T09:00")), result.occurrences.map { it.originalStart })
    }

    @Test
    fun `spring forward gap resolves forward and fall back overlap resolves to the first occurrence`() {
        assertEquals(Instant.parse("2025-03-09T07:30:00Z"), at("2025-03-09T02:30").toInstantIn(newYork))
        assertEquals(Instant.parse("2025-11-02T05:30:00Z"), at("2025-11-02T01:30").toInstantIn(newYork))
    }

    @Test
    fun `window filter is half open on the resolved instant`() {
        val window = TimeWindow(Instant.parse("2025-01-07T14:00:00Z"), Instant.parse("2025-01-08T14:00:00Z"))
        val result = request(window = window).assemble(listOf(at("2025-01-07T09:00"), at("2025-01-08T09:00")))
        assertEquals(listOf(at("2025-01-07T09:00")), result.occurrences.map { it.originalStart })
    }

    @Test
    fun `cap truncates and reports it`() {
        val starts = (7..20).map { at("2025-01-%02dT09:00".format(it)) }
        val capped = request(max = 5).assemble(starts)
        assertEquals(5, capped.occurrences.size)
        assertTrue(capped.truncated)
        assertFalse(request(max = 100).assemble(starts).truncated)
    }

    @Test
    fun `search range covers the window in the event zone with a pad`() {
        val range = request().localSearchRange()
        assertEquals(at("2024-12-29T19:00"), range.start)
        assertEquals(at("2026-01-02T19:00"), range.endInclusive)
    }

    @Test
    fun `invalid requests are rejected up front`() {
        assertFailsWith<IllegalArgumentException> { request(max = 0) }
        assertFailsWith<IllegalArgumentException> { request(max = RecurrenceRequest.HARD_MAX_INSTANCES + 1) }
        assertFailsWith<IllegalArgumentException> { request(allDay = true, start = "2025-01-06T09:00") }
        assertFailsWith<IllegalArgumentException> { TimeWindow(Instant.EPOCH, Instant.EPOCH) }
    }
}

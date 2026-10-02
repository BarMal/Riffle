package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [DefaultIcsEngine] with a fake weekly rule generator, so no recurrence library is involved. */
class IcsEngineTest {
    private val london = ZoneId.of("Europe/London")
    private val window = TimeWindow(Instant.parse("2026-01-12T00:00:00Z"), Instant.parse("2026-01-26T00:00:00Z"))
    private val expansion = IcsExpansion(window, london)

    private val weekly =
        RecurrenceExpander { request ->
            val end = LocalDateTime.of(2027, 1, 1, 0, 0)
            val starts = generateSequence(request.dtStart) { it.plusWeeks(1) }.takeWhile { it < end }
            request.assemble(starts.toList())
        }

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun event(
        start: String = "2026-01-05T09:00",
        rrule: String? = null,
        duration: Duration = Duration.ofHours(1),
        allDay: Boolean = false,
        exDates: Set<LocalDateTime> = emptySet(),
        overrides: List<IcsInstanceOverride> = emptyList(),
    ) = IcsEvent(
        uid = "u",
        title = "Weekly",
        location = null,
        start = at(start),
        zone = if (allDay) null else london,
        allDay = allDay,
        duration = duration,
        rrule = rrule,
        exDates = exDates,
        overrides = overrides,
    )

    private fun starts(occurrences: List<IcsOccurrence>) =
        occurrences.map { Instant.ofEpochMilli(it.startEpochMillis).toString() }

    @Test
    fun `a single event inside the window is kept and outside is dropped`() {
        val engine = DefaultIcsEngine(weekly)
        assertEquals(1, engine.expand(listOf(event(start = "2026-01-14T09:00")), expansion).size)
        assertEquals(0, engine.expand(listOf(event(start = "2026-02-14T09:00")), expansion).size)
        assertEquals(0, engine.expand(listOf(event(start = "2026-01-01T09:00")), expansion).size)
    }

    @Test
    fun `an event already running when the window opens is included`() {
        val running = event(start = "2026-01-11T23:00", duration = Duration.ofHours(3))
        assertEquals(1, DefaultIcsEngine(weekly).expand(listOf(running), expansion).size)
    }

    @Test
    fun `recurrence is expanded through the expander`() {
        val result = DefaultIcsEngine(weekly).expand(listOf(event(rrule = "FREQ=WEEKLY")), expansion)
        assertEquals(listOf("2026-01-12T09:00:00Z", "2026-01-19T09:00:00Z"), starts(result))
        assertTrue(result.all { it.recurring })
    }

    @Test
    fun `exdate removes an instance and an override moves one with its own text`() {
        val moved =
            IcsInstanceOverride(
                recurrenceId = at("2026-01-19T09:00"),
                start = at("2026-01-20T15:00"),
                duration = Duration.ofMinutes(30),
                title = "Moved",
                location = "Annex",
            )
        val result =
            DefaultIcsEngine(weekly).expand(
                listOf(
                    event(rrule = "FREQ=WEEKLY", exDates = setOf(at("2026-01-12T09:00")), overrides = listOf(moved)),
                ),
                expansion,
            )
        val only = result.single()
        assertEquals("2026-01-20T15:00:00Z", Instant.ofEpochMilli(only.startEpochMillis).toString())
        assertEquals("Moved", only.title)
        assertEquals("Annex", only.location)
        assertEquals(Duration.ofMinutes(30).toMillis(), only.endEpochMillis - only.startEpochMillis)
    }

    @Test
    fun `a cancelled override removes the instance`() {
        val cancelled = IcsInstanceOverride(at("2026-01-19T09:00"), cancelled = true)
        val result =
            DefaultIcsEngine(weekly).expand(
                listOf(event(rrule = "FREQ=WEEKLY", overrides = listOf(cancelled))),
                expansion,
            )
        assertEquals(listOf("2026-01-12T09:00:00Z"), starts(result))
    }

    @Test
    fun `without a working expander a recurring event shows only its own first instance`() {
        val first = event(start = "2026-01-14T09:00", rrule = "FREQ=WEEKLY")
        val later = event(start = "2026-01-05T09:00", rrule = "FREQ=WEEKLY")
        val engine = DefaultIcsEngine()
        assertEquals(1, engine.expand(listOf(first), expansion).size)
        assertEquals(0, engine.expand(listOf(later), expansion).size)
    }

    @Test
    fun `an expander that throws degrades like one that refuses`() {
        val engine = DefaultIcsEngine { error("boom") }
        assertEquals(1, engine.expand(listOf(event(start = "2026-01-14T09:00", rrule = "FREQ=WEEKLY")), expansion).size)
    }

    @Test
    fun `all day events end at the next local midnight and keep their flag`() {
        val trip = event(start = "2026-01-13T00:00", allDay = true, duration = Duration.ofDays(2))
        val only = DefaultIcsEngine(weekly).expand(listOf(trip), expansion).single()
        assertTrue(only.allDay)
        assertEquals(Duration.ofDays(2).toMillis(), only.endEpochMillis - only.startEpochMillis)
    }

    @Test
    fun `results are ordered and capped`() {
        val many = (0 until 30).map { i -> event(start = "2026-01-%02dT09:00".format(12 + i % 10)).copy(uid = "e$i") }
        val result = DefaultIcsEngine(weekly).expand(many, IcsExpansion(window, london, maxOccurrences = 7))
        assertEquals(7, result.size)
        assertEquals(result.sortedBy { it.startEpochMillis }, result)
    }
}

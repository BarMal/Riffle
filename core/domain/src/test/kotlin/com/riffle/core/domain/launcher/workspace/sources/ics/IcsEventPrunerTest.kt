package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class IcsEventPrunerTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-10T12:00:00Z")

    private fun event(
        uid: String,
        start: String,
        rrule: String? = null,
        allDay: Boolean = false,
        duration: Duration = Duration.ofHours(1),
    ) = IcsEvent(
        uid = uid,
        title = uid,
        location = null,
        start = LocalDateTime.parse(start),
        zone = utc,
        allDay = allDay,
        duration = duration,
        rrule = rrule,
    )

    @Test
    fun `old single events are dropped and recurring ones always stay`() {
        val kept =
            IcsEventPruner.prune(
                listOf(
                    event("old", "2026-01-01T09:00"),
                    event("recent", "2026-03-10T09:00"),
                    event("yesterday", "2026-03-09T09:00"),
                    event("future", "2026-04-01T09:00"),
                    event("weekly", "2025-01-01T09:00", rrule = "FREQ=WEEKLY"),
                ),
                now,
                utc,
            )
        assertEquals(listOf("weekly", "recent", "future"), kept.map { it.uid })
    }

    @Test
    fun `a multi day all day event still running is kept`() {
        val trip = event("trip", "2026-03-08T00:00", allDay = true, duration = Duration.ofDays(5))
        assertEquals(listOf("trip"), IcsEventPruner.prune(listOf(trip), now, utc).map { it.uid })
    }

    @Test
    fun `the cap keeps recurring events then the soonest singles`() {
        val events =
            listOf(event("late", "2026-09-01T09:00"), event("soon", "2026-03-11T09:00")) +
                event("weekly", "2025-01-01T09:00", rrule = "FREQ=WEEKLY")
        assertEquals(listOf("weekly", "soon"), IcsEventPruner.prune(events, now, utc, maxEvents = 2).map { it.uid })
    }
}

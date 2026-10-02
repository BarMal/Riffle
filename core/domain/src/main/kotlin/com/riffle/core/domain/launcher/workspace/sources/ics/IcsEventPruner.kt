package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Chooses what a feed refresh keeps in the device cache: recurring events (their later instances are expanded
 * when read), and single events that have not ended more than [GRACE] ago, soonest first, at most [maxEvents]
 * in all. Old single events are dropped here so the cache holds only what a window could still show.
 */
object IcsEventPruner {
    val GRACE: Duration = Duration.ofDays(1)
    const val DEFAULT_MAX_EVENTS = 1_000

    fun prune(
        events: List<IcsEvent>,
        now: Instant,
        deviceZone: ZoneId,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
    ): List<IcsEvent> {
        val (recurring, single) = events.partition(IcsEvent::isRecurring)
        val cutoff = now.minus(GRACE)
        val upcoming =
            single
                .filter { event -> endOf(event, deviceZone) >= cutoff }
                .sortedBy { event -> event.start }
        return (recurring + upcoming).take(maxEvents.coerceAtLeast(0))
    }

    private fun endOf(
        event: IcsEvent,
        deviceZone: ZoneId,
    ): Instant {
        val zone = event.zone ?: deviceZone
        val end = if (event.allDay) event.start.plusDays(event.duration.toDays()) else event.start
        return end.toInstantIn(zone).plus(if (event.allDay) Duration.ZERO else event.duration)
    }
}

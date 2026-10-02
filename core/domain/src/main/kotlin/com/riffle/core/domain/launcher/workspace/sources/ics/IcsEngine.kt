package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** One event instance that overlaps the expansion window. Times are absolute; all-day ones are local midnights. */
data class IcsOccurrence(
    val uid: String,
    val title: String,
    val location: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val isPrivate: Boolean,
    val recurring: Boolean,
)

/** What to expand: instances overlapping [window], at most [maxOccurrences] in total. */
data class IcsExpansion(
    val window: TimeWindow,
    val deviceZone: ZoneId,
    val maxOccurrences: Int = DEFAULT_MAX_OCCURRENCES,
) {
    init {
        require(maxOccurrences > 0) { "maxOccurrences must be positive." }
    }

    companion object {
        const val DEFAULT_MAX_OCCURRENCES = 200
    }
}

/**
 * Reads iCalendar text and expands recurrence. The launcher has no other seam to calendar parsing; the
 * source and its tests talk to this interface only. Implementations are pure and must not throw.
 */
interface IcsEngine {
    fun parse(
        text: String,
        deviceZone: ZoneId,
    ): IcsParseResult

    fun expand(
        events: List<IcsEvent>,
        expansion: IcsExpansion,
    ): List<IcsOccurrence>
}

/**
 * [IcsParser] for reading and a [RecurrenceExpander] (the `core/recurrence-ical4j` implementation in the app)
 * for recurrence. Without an expander (or when it refuses a rule) a recurring event shows only its first
 * instance, when that falls in the window: degraded, never wrong.
 */
class DefaultIcsEngine(
    private val expander: RecurrenceExpander = NO_RECURRENCE,
) : IcsEngine {
    override fun parse(
        text: String,
        deviceZone: ZoneId,
    ): IcsParseResult = IcsParser.parse(text, deviceZone)

    override fun expand(
        events: List<IcsEvent>,
        expansion: IcsExpansion,
    ): List<IcsOccurrence> {
        val all = events.flatMap { event -> occurrencesOf(event, expansion) }
        return all
            .sortedWith(compareBy<IcsOccurrence> { it.startEpochMillis }.thenBy { it.uid })
            .take(expansion.maxOccurrences)
    }

    private fun occurrencesOf(
        event: IcsEvent,
        expansion: IcsExpansion,
    ): List<IcsOccurrence> {
        val zone = event.zone ?: expansion.deviceZone
        val single = listOf(Instance(event.start, event.duration, event.title, event.location))
        val instances =
            if (!event.isRecurring) {
                single
            } else {
                expandRecurring(event, zone, expansion) ?: single
            }
        return instances.mapNotNull { it.toOccurrence(event, zone, expansion.window) }
    }

    private fun expandRecurring(
        event: IcsEvent,
        zone: ZoneId,
        expansion: IcsExpansion,
    ): List<Instance>? {
        // Start the search one event length early so an instance already running at window start is found.
        val from = expansion.window.from.minus(event.duration)
        val request =
            RecurrenceRequest(
                rrule = event.rrule,
                dtStart = event.start,
                allDay = event.allDay,
                zone = zone,
                exDates = event.exDates,
                rDates = event.rDates,
                overrides = event.overrides.map { RecurrenceOverride(it.recurrenceId, replacement(it)) },
                window = TimeWindow(from, expansion.window.to),
                maxInstances = expansion.maxOccurrences.coerceAtMost(RecurrenceRequest.HARD_MAX_INSTANCES),
            )
        val result = runCatching { expander.expand(request) }.getOrNull() as? RecurrenceResult.Ok ?: return null
        val byId = event.overrides.associateBy { it.recurrenceId }
        return result.occurrences.map { occurrence ->
            val override = byId[occurrence.originalStart]
            Instance(
                start = override?.start ?: occurrence.originalStart,
                duration = override?.duration ?: event.duration,
                title = override?.title ?: event.title,
                location = override?.location ?: event.location,
            )
        }
    }

    private fun replacement(override: IcsInstanceOverride): LocalDateTime? =
        if (override.cancelled) null else override.start ?: override.recurrenceId

    private class Instance(
        val start: LocalDateTime,
        val duration: Duration,
        val title: String,
        val location: String?,
    ) {
        fun toOccurrence(
            event: IcsEvent,
            zone: ZoneId,
            window: TimeWindow,
        ): IcsOccurrence? {
            val startInstant = start.toInstantIn(zone)
            val endInstant =
                if (event.allDay) {
                    start.plusDays(duration.toDays()).toInstantIn(zone)
                } else {
                    startInstant.plus(duration)
                }
            // Zero-length events count as running at their start instant.
            val overlaps = startInstant < window.to && (endInstant > window.from || startInstant >= window.from)
            return if (overlaps) {
                IcsOccurrence(
                    uid = event.uid,
                    title = title,
                    location = location,
                    startEpochMillis = startInstant.toEpochMilli().coerceAtLeast(0L),
                    endEpochMillis = endInstant.toEpochMilli().coerceAtLeast(0L),
                    allDay = event.allDay,
                    isPrivate = event.isPrivate,
                    recurring = event.isRecurring,
                )
            } else {
                null
            }
        }
    }
}

/** Refuses every rule, so [DefaultIcsEngine] shows recurring events by their first instance only. */
private val NO_RECURRENCE = RecurrenceExpander { RecurrenceResult.Failed(RecurrenceFailure.UNSUPPORTED_RULE) }

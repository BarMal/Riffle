package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Expands an iCalendar (RFC 5545) recurrence set for a bounded window. This is the only seam the launcher
 * has to a recurrence library: no library type crosses it, and `core/domain` does not depend on any. The
 * implementation lives in `core/recurrence-ical4j`.
 *
 * Implementations must be pure (no I/O, no clock, no logging of event content), must never loop without
 * bound, and must report bad input as [RecurrenceResult.Failed] rather than throwing.
 */
fun interface RecurrenceExpander {
    fun expand(request: RecurrenceRequest): RecurrenceResult
}

/** Half-open window `[from, to)` of instants an expansion is asked for. */
data class TimeWindow(
    val from: Instant,
    val to: Instant,
) {
    init {
        require(from < to) { "A time window must not be empty or inverted." }
    }

    operator fun contains(instant: Instant): Boolean = instant >= from && instant < to
}

/**
 * A moved or cancelled occurrence (an iCalendar `RECURRENCE-ID` override). [recurrenceId] is the original
 * wall-clock start the rule would have produced; [replacementStart] is where the instance now starts, or
 * null when the instance was cancelled.
 */
data class RecurrenceOverride(
    val recurrenceId: LocalDateTime,
    val replacementStart: LocalDateTime?,
)

/**
 * What to expand. Wall-clock values are in [zone] (the `TZID`, UTC for a `Z` start, or the device zone for
 * floating and all-day events), because recurrence is defined on wall-clock time: a daily 09:00 event stays
 * at 09:00 across a DST change and its instants move.
 *
 * For an all-day event ([allDay]) every [LocalDateTime] must be at start of day; instants are then the start
 * of that date in [zone].
 */
data class RecurrenceRequest(
    /** The `RRULE` value without the `RRULE:` prefix, or null for a single event with only [rDates]. */
    val rrule: String?,
    val dtStart: LocalDateTime,
    val allDay: Boolean,
    val zone: ZoneId,
    val exDates: Set<LocalDateTime> = emptySet(),
    val rDates: Set<LocalDateTime> = emptySet(),
    val overrides: List<RecurrenceOverride> = emptyList(),
    val window: TimeWindow,
    val maxInstances: Int = DEFAULT_MAX_INSTANCES,
) {
    init {
        require(maxInstances in 1..HARD_MAX_INSTANCES) { "maxInstances must be in 1..$HARD_MAX_INSTANCES." }
        require(!allDay || dtStart.toLocalTime().toSecondOfDay() == 0) { "All-day starts must be at start of day." }
    }

    companion object {
        const val DEFAULT_MAX_INSTANCES = 500
        const val HARD_MAX_INSTANCES = 10_000
    }
}

/** One expanded instance. [originalStart] is its `RECURRENCE-ID` (what an override would name). */
data class Occurrence(
    val start: Instant,
    val originalStart: LocalDateTime,
)

enum class RecurrenceFailure {
    /** The rule text could not be parsed or is inconsistent. */
    INVALID_RULE,

    /** A valid rule this implementation refuses (sub-daily frequency, non-Gregorian `RSCALE`). */
    UNSUPPORTED_RULE,
}

sealed interface RecurrenceResult {
    /** [occurrences] are ordered by start and unique; [truncated] means [RecurrenceRequest.maxInstances] cut it. */
    data class Ok(
        val occurrences: List<Occurrence>,
        val truncated: Boolean,
    ) : RecurrenceResult

    /** Never carries a message: rule text is user content and must not reach logs or diagnostics. */
    data class Failed(
        val reason: RecurrenceFailure,
    ) : RecurrenceResult
}

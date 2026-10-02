package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One calendar event as read from a feed, in wall-clock terms (what RFC 5545 recurrence is defined on).
 * [zone] is null for floating and all-day events, which take the device zone when they are expanded.
 * Transient feed content: it is cached on the device only, never backed up (see
 * `docs/product/workspaces-ics-source.md`).
 */
data class IcsEvent(
    val uid: String,
    val title: String,
    val location: String?,
    val start: LocalDateTime,
    val zone: ZoneId?,
    val allDay: Boolean,
    /** Timed events: the length. All-day events: whole days (at least one). */
    val duration: Duration,
    /** The `RRULE` value without its prefix; null for a single event. */
    val rrule: String?,
    val exDates: Set<LocalDateTime> = emptySet(),
    val rDates: Set<LocalDateTime> = emptySet(),
    val overrides: List<IcsInstanceOverride> = emptyList(),
    /** `CLASS:PRIVATE` or `CLASS:CONFIDENTIAL`. */
    val isPrivate: Boolean = false,
) {
    val isRecurring: Boolean get() = rrule != null || rDates.isNotEmpty() || overrides.isNotEmpty()
}

/**
 * A `RECURRENCE-ID` override of one instance. [start] is null when the instance keeps its original start;
 * [cancelled] instances are removed. Absent [duration], [title] and [location] inherit from the master.
 */
data class IcsInstanceOverride(
    val recurrenceId: LocalDateTime,
    val cancelled: Boolean = false,
    val start: LocalDateTime? = null,
    val duration: Duration? = null,
    val title: String? = null,
    val location: String? = null,
)

enum class IcsParseFailure {
    /** No `VCALENDAR` was found: the body is not an iCalendar document. */
    NOT_CALENDAR,

    /** The text is longer than [IcsLimits.MAX_INPUT_CHARS]. */
    TOO_LARGE,
}

sealed interface IcsParseResult {
    /**
     * [skippedEvents] counts events dropped as malformed, [unknownZoneEvents] events whose `TZID` could not be
     * mapped (they are read as floating), [truncated] that [IcsLimits.MAX_EVENTS] cut the list.
     */
    data class Ok(
        val events: List<IcsEvent>,
        val skippedEvents: Int = 0,
        val unknownZoneEvents: Int = 0,
        val truncated: Boolean = false,
    ) : IcsParseResult

    /** Never carries a message: feed text is user content and must not reach logs. */
    data class Failed(
        val reason: IcsParseFailure,
    ) : IcsParseResult
}

/** Hard bounds on untrusted feed text. Nothing here is configurable from a feed. */
object IcsLimits {
    const val MAX_INPUT_CHARS = 4_000_000
    const val MAX_LINE_CHARS = 8_192
    const val MAX_EVENTS = 5_000
    const val MAX_TEXT_CHARS = 256
    const val MAX_RRULE_CHARS = 1_024
    const val MAX_EX_DATES = 2_000
    const val MAX_R_DATES = 500
    const val MAX_OVERRIDES = 1_000
    const val MAX_COMPONENT_DEPTH = 8
    const val MAX_DURATION_DAYS = 366L
}

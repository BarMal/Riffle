package com.riffle.app.launcher.calendar

import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One `CalendarContract.Instances` row, free of Android types so the mapping is testable on the JVM.
 * Transient: rows are turned into [CalendarEvent]s and dropped, never stored.
 */
internal data class CalendarInstanceRow(
    val eventId: Long,
    val title: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val location: String?,
    val allDay: Boolean,
    /** `CalendarContract.Events.ACCESS_LEVEL`. */
    val accessLevel: Int,
)

/** Values of `CalendarContract.Events.ACCESS_CONFIDENTIAL` and `ACCESS_PRIVATE`. */
private const val ACCESS_CONFIDENTIAL = 1
private const val ACCESS_PRIVATE = 2

internal fun Int.isPrivateAccessLevel(): Boolean = this == ACCESS_CONFIDENTIAL || this == ACCESS_PRIVATE

/**
 * Maps a row to a launcher event, or null when its times are unusable.
 *
 * The item id includes the start time because a recurring event yields several instances that share one
 * event id. All-day instances are stored as UTC midnights, so they are moved to local midnights; otherwise
 * an all-day event would appear to start and end hours off in any other time zone.
 */
internal fun CalendarInstanceRow.toCalendarEvent(zone: ZoneId): CalendarEvent? {
    val start = if (allDay) beginMillis.utcDateStartIn(zone) else beginMillis
    val end = if (allDay) endMillis.utcDateStartIn(zone) else endMillis
    return if (start < 0L || eventId < 0L) {
        null
    } else {
        CalendarEvent(
            id = "$eventId:$beginMillis",
            eventId = eventId.toString(),
            title = title.orEmpty(),
            startEpochMillis = start,
            endEpochMillis = end.coerceAtLeast(start),
            location = location,
            allDay = allDay,
            isPrivate = accessLevel.isPrivateAccessLevel(),
        )
    }
}

private fun Long.utcDateStartIn(zone: ZoneId): Long =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

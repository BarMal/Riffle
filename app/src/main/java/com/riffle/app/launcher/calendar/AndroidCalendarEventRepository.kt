package com.riffle.app.launcher.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.os.Looper
import android.provider.CalendarContract
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.CalendarEventRepository
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Reads upcoming events from the calendar provider through `CalendarContract.Instances`, which expands
 * recurring events. Requires `READ_CALENDAR`; callers gate on the permission first and this class never
 * requests it. Blocking: it refuses to run on the main thread.
 *
 * Only a bounded window ahead of the query time is read, and at most [MAX_ROWS] rows, so a busy calendar
 * cannot make a refresh expensive. Hidden (not visible) calendars are skipped.
 */
internal class AndroidCalendarEventRepository(
    private val contentResolver: ContentResolver,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val windowMillis: Long = TimeUnit.DAYS.toMillis(WINDOW_DAYS),
) : CalendarEventRepository {
    override fun upcomingEvents(
        fromEpochMillis: Long,
        limit: Int,
    ): List<CalendarEvent> {
        check(Looper.getMainLooper().thread !== Thread.currentThread()) {
            "Calendar queries must not run on the main thread."
        }
        val uri =
            CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { builder ->
                    ContentUris.appendId(builder, fromEpochMillis)
                    ContentUris.appendId(builder, fromEpochMillis + windowMillis)
                }
                .build()
        val rows =
            contentResolver.query(uri, PROJECTION, VISIBLE_ONLY, null, SORT_ORDER)
                ?.use { cursor -> cursor.readRows() }
                .orEmpty()
        val zoneId = zone()
        return rows
            .mapNotNull { row -> row.toCalendarEvent(zoneId) }
            .filter { event -> event.endEpochMillis > fromEpochMillis }
            .sortedWith(compareBy<CalendarEvent> { it.startEpochMillis }.thenBy { it.id })
            .take(limit.coerceAtLeast(0))
    }

    private fun Cursor.readRows(): List<CalendarInstanceRow> {
        val rows = ArrayList<CalendarInstanceRow>()
        while (moveToNext() && rows.size < MAX_ROWS) {
            rows +=
                CalendarInstanceRow(
                    eventId = getLong(COLUMN_EVENT_ID),
                    title = getString(COLUMN_TITLE),
                    beginMillis = getLong(COLUMN_BEGIN),
                    endMillis = getLong(COLUMN_END),
                    location = getString(COLUMN_LOCATION),
                    allDay = getInt(COLUMN_ALL_DAY) != 0,
                    accessLevel = getInt(COLUMN_ACCESS_LEVEL),
                )
        }
        return rows
    }

    private companion object {
        const val WINDOW_DAYS = 7L
        const val MAX_ROWS = 200

        const val COLUMN_EVENT_ID = 0
        const val COLUMN_TITLE = 1
        const val COLUMN_BEGIN = 2
        const val COLUMN_END = 3
        const val COLUMN_LOCATION = 4
        const val COLUMN_ALL_DAY = 5
        const val COLUMN_ACCESS_LEVEL = 6

        val PROJECTION =
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.ACCESS_LEVEL,
            )
        const val VISIBLE_ONLY = "${CalendarContract.Calendars.VISIBLE} = 1"
        const val SORT_ORDER = "${CalendarContract.Instances.BEGIN} ASC"
    }
}

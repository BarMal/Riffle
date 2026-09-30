package com.riffle.app.launcher.calendar

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.riffle.app.launcher.sources.SourceChangeSource

/**
 * Reports calendar provider changes. A [ContentObserver] is registered per [observe] call and removed by
 * the returned function; the shared stream calls it only while it has observers. Registering reads no
 * calendar data, and the callback only schedules a reload, which the source gates on the permission.
 */
internal class AndroidCalendarChangeSource(
    private val contentResolver: ContentResolver,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : SourceChangeSource {
    override fun observe(onChanged: () -> Unit): () -> Unit {
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    onChanged()
                }
            }
        val registered =
            runCatching {
                contentResolver.registerContentObserver(CALENDAR_URI, true, observer)
            }.isSuccess
        return {
            if (registered) runCatching { contentResolver.unregisterContentObserver(observer) }
        }
    }

    private companion object {
        val CALENDAR_URI: Uri = Uri.parse("content://${CalendarContract.AUTHORITY}")
    }
}

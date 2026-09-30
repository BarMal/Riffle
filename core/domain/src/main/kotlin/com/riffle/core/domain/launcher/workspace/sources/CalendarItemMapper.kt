package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/** A calendar event as the platform reports it. Transient: never stored by the launcher. */
data class CalendarEvent(
    val id: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String? = null,
    val allDay: Boolean = false,
    /** The calendar marks the event private or confidential. */
    val isPrivate: Boolean = false,
    /** The calendar event this is an occurrence of; differs from [id] for recurring instances. */
    val eventId: String = id,
) {
    init {
        require(id.isNotBlank()) { "Calendar event ids must not be blank." }
        require(startEpochMillis >= 0L) { "Calendar event start cannot be negative." }
        require(eventId.isNotBlank()) { "Calendar event ids must not be blank." }
        require(endEpochMillis >= startEpochMillis) { "Calendar event cannot end before it starts." }
    }
}

/** Platform seam for reading upcoming events; implementations must be called off the main thread. */
fun interface CalendarEventRepository {
    /** Events that have not ended at [fromEpochMillis], soonest first, at most [limit]. */
    fun upcomingEvents(
        fromEpochMillis: Long,
        limit: Int,
    ): List<CalendarEvent>
}

/** Maps calendar events to [Item]s. Private events are [ItemPrivacy.SENSITIVE]; text is stripped at projection. */
class CalendarItemMapper {
    /** The next [limit] events that have not ended at [nowEpochMillis]: ongoing first, then by start. */
    fun nextEvents(
        events: List<CalendarEvent>,
        nowEpochMillis: Long,
        limit: Int = 1,
    ): List<Item> =
        events
            .filter { event -> event.endEpochMillis > nowEpochMillis }
            .sortedWith(compareBy<CalendarEvent> { it.startEpochMillis }.thenBy { it.id })
            .take(limit.coerceAtLeast(0))
            .map { event -> event.toItem() }

    private fun CalendarEvent.toItem(): Item =
        Item(
            id = ItemId("${SourceIds.CALENDAR.value}:$id"),
            sourceId = SourceIds.CALENDAR,
            target = ItemTarget.DeepLink("content://com.android.calendar/events/$eventId"),
            title = title.takeIf(String::isNotBlank),
            subtitle = location?.takeIf(String::isNotBlank),
            timeEpochMillis = startEpochMillis,
            actions = listOf(ItemAction.Open()),
            privacy = if (isPrivate) ItemPrivacy.SENSITIVE else ItemPrivacy.VISIBLE,
            ext =
                mapOf(
                    END_KEY to ItemExtValue.Number(endEpochMillis),
                    ALL_DAY_KEY to ItemExtValue.Flag(allDay),
                ),
        )

    private companion object {
        val END_KEY = ItemExtKey("calendar.end")
        val ALL_DAY_KEY = ItemExtKey("calendar.all_day")
    }
}

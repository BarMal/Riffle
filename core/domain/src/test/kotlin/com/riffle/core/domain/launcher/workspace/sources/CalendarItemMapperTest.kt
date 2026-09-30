package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalendarItemMapperTest {
    private val mapper = CalendarItemMapper()

    private fun event(
        id: String,
        start: Long,
        end: Long = start + 100,
        private: Boolean = false,
    ) = CalendarEvent(id = id, title = "Event $id", startEpochMillis = start, endEpochMillis = end, isPrivate = private)

    @Test
    fun `next event skips ended events and prefers the ongoing or soonest`() {
        val items =
            mapper.nextEvents(
                listOf(event("past", 0, 50), event("later", 900), event("now", 400, 600), event("soon", 500)),
                nowEpochMillis = 500,
                limit = 2,
            )

        assertEquals(listOf("calendar:now", "calendar:soon"), items.map { it.id.value })
        assertEquals(SourceIds.CALENDAR, items.first().sourceId)
        assertEquals(400L, items.first().timeEpochMillis)
        assertEquals(ItemTarget.DeepLink("content://com.android.calendar/events/now"), items.first().target)
        assertEquals(ItemExtValue.Number(600), items.first().ext[ItemExtKey("calendar.end")])
    }

    @Test
    fun `private events are sensitive and default limit is one`() {
        val items = mapper.nextEvents(listOf(event("a", 10, private = true), event("b", 20)), nowEpochMillis = 0)
        assertEquals(1, items.size)
        assertEquals(ItemPrivacy.SENSITIVE, items.single().privacy)
    }

    @Test
    fun `recurring instances keep distinct item ids but link to the same event`() {
        val items =
            mapper.nextEvents(
                listOf(
                    CalendarEvent("7:100", "Standup", 100, 200, eventId = "7"),
                    CalendarEvent("7:300", "Standup", 300, 400, eventId = "7"),
                ),
                nowEpochMillis = 0,
                limit = 2,
            )

        assertEquals(listOf("calendar:7:100", "calendar:7:300"), items.map { it.id.value })
        assertEquals(
            setOf<ItemTarget>(ItemTarget.DeepLink("content://com.android.calendar/events/7")),
            items.map { it.target }.toSet(),
        )
    }

    @Test
    fun `no upcoming events yields no items`() {
        assertTrue(mapper.nextEvents(listOf(event("past", 0, 5)), nowEpochMillis = 10).isEmpty())
    }

    @Test
    fun `events reject impossible times`() {
        assertFailsWith<IllegalArgumentException> { event("x", start = 10, end = 5) }
    }
}

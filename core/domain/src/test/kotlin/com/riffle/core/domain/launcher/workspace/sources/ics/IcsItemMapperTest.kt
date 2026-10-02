package com.riffle.core.domain.launcher.workspace.sources.ics

import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsItemMapperTest {
    private fun occurrence(
        uid: String,
        start: Long,
        end: Long = start + 1_000,
        title: String = "Event $uid",
        location: String? = null,
        isPrivate: Boolean = false,
        allDay: Boolean = false,
    ) = IcsOccurrence(uid, title, location, start, end, allDay, isPrivate, recurring = false)

    private val work = IcsFeedId("work")
    private val home = IcsFeedId("home")

    @Test
    fun `items are ordered by start across feeds and carry feed grouping`() {
        val items =
            IcsItemMapper().items(
                listOf(
                    IcsFeedOccurrences(work, "Work", listOf(occurrence("b", 3_000), occurrence("a", 1_000))),
                    IcsFeedOccurrences(home, "Home", listOf(occurrence("c", 2_000))),
                ),
                nowEpochMillis = 0,
            )
        assertEquals(listOf("Event a", "Event c", "Event b"), items.map { it.title })
        assertEquals(listOf("work", "home", "work"), items.map { it.groupKey })
        assertTrue(items.all { it.sourceId == SourceIds.ICS && it.target == ItemTarget.None })
    }

    @Test
    fun `ended events are dropped and ongoing ones kept`() {
        val items =
            IcsItemMapper().items(
                listOf(
                    IcsFeedOccurrences(work, "Work", listOf(occurrence("past", 100, 200), occurrence("now", 100, 900))),
                ),
                nowEpochMillis = 500,
            )
        assertEquals(listOf("Event now"), items.map { it.title })
    }

    @Test
    fun `private and confidential events are sensitive like the device calendar`() {
        val items =
            IcsItemMapper().items(
                listOf(
                    IcsFeedOccurrences(
                        work,
                        "Work",
                        listOf(occurrence("p", 1_000, isPrivate = true), occurrence("v", 2_000)),
                    ),
                ),
                nowEpochMillis = 0,
            )
        assertEquals(listOf(ItemPrivacy.SENSITIVE, ItemPrivacy.VISIBLE), items.map { it.privacy })
    }

    @Test
    fun `location is the subtitle with the feed name as fallback and extras match the calendar keys`() {
        val items =
            IcsItemMapper().items(
                listOf(
                    IcsFeedOccurrences(
                        work,
                        "Work",
                        listOf(occurrence("a", 1_000, location = "Room 4", allDay = true), occurrence("b", 2_000)),
                    ),
                ),
                nowEpochMillis = 0,
            )
        assertEquals(listOf("Room 4", "Work"), items.map { it.subtitle })
        assertEquals(ItemExtValue.Flag(true), items[0].ext[IcsItemMapper.ALL_DAY_KEY])
        assertEquals(ItemExtValue.Number(3_000), items[1].ext[IcsItemMapper.END_KEY])
    }

    @Test
    fun `ids are unique per instance and the limit is applied`() {
        val repeats = (0 until 5).map { occurrence("same", 1_000L * (it + 1)) }
        val items = IcsItemMapper().items(listOf(IcsFeedOccurrences(work, "Work", repeats)), 0, limit = 3)
        assertEquals(3, items.map { it.id }.toSet().size)
    }

    @Test
    fun `blank titles become null`() {
        val blank = IcsFeedOccurrences(work, "Work", listOf(occurrence("a", 1, title = "")))
        val items = IcsItemMapper().items(listOf(blank), 0)
        assertEquals(null, items.single().title)
    }
}

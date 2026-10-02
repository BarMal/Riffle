package com.riffle.core.domain.launcher.workspace.sources.ics

import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class IcsSourceReaderTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val reader = IcsSourceReader(DefaultIcsEngine())

    private fun event(
        uid: String,
        start: String,
        isPrivate: Boolean = false,
        duration: Duration = Duration.ofHours(1),
    ) = IcsEvent(uid, "T-$uid", null, LocalDateTime.parse(start), utc, false, duration, null, isPrivate = isPrivate)

    private fun read(
        vararg events: IcsEvent,
        windowDays: Long = 14,
        limit: Int = 50,
    ) = reader.items(
        listOf(IcsFeedEvents(IcsFeedId("f"), "Feed", events.toList())),
        now.toEpochMilli(),
        utc,
        windowDays,
        limit,
    )

    @Test
    fun `no feeds and no events read as nothing`() {
        assertEquals(emptyList(), reader.items(emptyList(), now.toEpochMilli(), utc))
        assertEquals(emptyList(), read())
    }

    @Test
    fun `only running and upcoming events inside the window are shown in order`() {
        val items =
            read(
                event("past", "2026-03-10T08:00"),
                event("running", "2026-03-10T11:30"),
                event("tomorrow", "2026-03-11T09:00"),
                event("far", "2026-05-01T09:00"),
            )
        assertEquals(listOf("T-running", "T-tomorrow"), items.map { it.title })
    }

    @Test
    fun `the window length decides what is far`() {
        val soonish = event("soonish", "2026-03-30T09:00")
        assertEquals(0, read(soonish, windowDays = 14).size)
        assertEquals(1, read(soonish, windowDays = 30).size)
    }

    @Test
    fun `the item cap applies`() {
        val many = (1..10).map { event("e$it", "2026-03-%02dT09:00".format(10 + it)) }
        assertEquals(4, read(*many.toTypedArray(), limit = 4).size)
    }

    @Test
    fun `privacy is carried to the item`() {
        val items = read(event("p", "2026-03-11T09:00", isPrivate = true), event("v", "2026-03-12T09:00"))
        assertEquals(listOf(ItemPrivacy.SENSITIVE, ItemPrivacy.VISIBLE), items.map { it.privacy })
    }
}

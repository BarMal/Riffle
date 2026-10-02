package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsInstanceOverride
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class IcsJsonCodecsTest {
    @Test
    fun settingsRoundTrip() {
        val settings = settingsWithOne().withEnabled(IcsFeedId("feed-1"), false)
        val decoded = IcsJsonCodecs.decodeSettings(IcsJsonCodecs.encodeSettings(settings))
        assertEquals(settings, decoded)
        assertFalse(decoded!!.feeds.single().enabled)
    }

    @Test
    fun anUnknownVersionOrGarbageReadsAsNothing() {
        assertNull(IcsJsonCodecs.decodeSettings("""{"version":99,"feeds":[]}"""))
        assertNull(IcsJsonCodecs.decodeSettings("not json"))
        assertNull(IcsJsonCodecs.decodeSettings(""))
        assertNull(IcsJsonCodecs.decodeCache("""{"version":2}"""))
        assertNull(IcsJsonCodecs.decodeCache("[]"))
    }

    @Test
    fun badEntriesAreDroppedAndEveryUrlIsRevalidated() {
        val text =
            """{"version":1,"feeds":[
              {"id":"a","name":"Ok","url":"https://example.com/a.ics","enabled":true},
              {"id":"b","name":"Http","url":"http://example.com/b.ics"},
              {"id":"c","name":"Local","url":"https://localhost/c.ics"},
              {"id":"d","name":"Creds","url":"https://u:p@example.com/d.ics"},
              {"id":"a","name":"DupId","url":"https://example.com/e.ics"},
              {"name":"NoId","url":"https://example.com/f.ics"},
              "junk", 5, null]}"""
        val decoded = IcsJsonCodecs.decodeSettings(text)!!
        assertEquals(listOf("a"), decoded.feeds.map { it.id.value })
    }

    @Test
    fun theFeedListIsBoundedOnDecode() {
        val feeds =
            (0 until 40).joinToString(
                ",",
            ) { """{"id":"f$it","name":"n","url":"https://e$it.example.com/a.ics"}""" }
        val decoded = IcsJsonCodecs.decodeSettings("""{"version":1,"feeds":[$feeds]}""")!!
        assertEquals(IcsFeedSettings.MAX_ICS_FEEDS, decoded.feeds.size)
    }

    @Test
    fun cacheRoundTripKeepsRulesExdatesAndOverrides() {
        val event =
            IcsEvent(
                uid = "u",
                title = "Weekly",
                location = "Room",
                start = LocalDateTime.parse("2026-03-02T09:00"),
                zone = ZoneId.of("Europe/London"),
                allDay = false,
                duration = Duration.ofMinutes(45),
                rrule = "FREQ=WEEKLY;BYDAY=MO",
                exDates = setOf(LocalDateTime.parse("2026-03-09T09:00")),
                rDates = setOf(LocalDateTime.parse("2026-03-12T15:00")),
                overrides =
                    listOf(
                        IcsInstanceOverride(
                            LocalDateTime.parse("2026-03-16T09:00"),
                            start = LocalDateTime.parse("2026-03-16T11:00"),
                            title = "Moved",
                        ),
                        IcsInstanceOverride(LocalDateTime.parse("2026-03-23T09:00"), cancelled = true),
                    ),
                isPrivate = true,
            )
        val floating = icsEvent("f").copy(zone = null, allDay = true, duration = Duration.ofDays(2))
        val cache = mapOf(IcsFeedId("feed-1") to CachedIcsFeed(listOf(event, floating), 1234L))
        assertEquals(cache, IcsJsonCodecs.decodeCache(IcsJsonCodecs.encodeCache(cache)))
    }

    @Test
    fun theCacheDocumentNeverContainsAUrl() {
        val cache = mapOf(IcsFeedId("feed-1") to CachedIcsFeed(listOf(icsEvent("a")), 1L))
        val json = IcsJsonCodecs.encodeCache(cache)
        assertFalse(json.contains("https"))
        assertFalse(json.contains("SECRET"))
    }

    @Test
    fun anUnreadableEventIsDroppedNotTheFeed() {
        val text =
            """{"version":1,"feeds":[{"id":"f","at":5,"events":[
              {"u":"x","t":"ok","s":"2026-03-02T09:00","a":false,"d":60},
              {"u":"bad","t":"t","s":"not a date","a":false,"d":1},
              {"u":"badzone","t":"t","s":"2026-03-02T09:00","z":"Mars/Base","a":false,"d":1},
              {}, 7]}]}"""
        val feed = IcsJsonCodecs.decodeCache(text)!!.getValue(IcsFeedId("f"))
        assertEquals(listOf("x"), feed.events.map { it.uid })
    }

    @Test
    fun eventCountsAndTextAreBoundedOnDecode() {
        val many =
            (0 until IcsJsonCodecs.MAX_CACHED_EVENTS_PER_FEED + 50).joinToString(",") {
                """{"u":"e$it","t":"${"x".repeat(1000)}","s":"2026-03-02T09:00","a":false,"d":60}"""
            }
        val feed =
            IcsJsonCodecs.decodeCache("""{"version":1,"feeds":[{"id":"f","at":5,"events":[$many]}]}""")!!
                .getValue(IcsFeedId("f"))
        assertEquals(IcsJsonCodecs.MAX_CACHED_EVENTS_PER_FEED, feed.events.size)
        assertTrue(feed.events.all { it.title.length <= 256 })
    }

    @Test
    fun theSettingsDocumentIsTheOnlyPlaceAUrlIsWritten() {
        val json = JSONObject(IcsJsonCodecs.encodeSettings(settingsWithOne()))
        assertEquals(1, json.getInt("version"))
        assertTrue(json.toString().contains("SECRET-TOKEN-123"))
    }
}

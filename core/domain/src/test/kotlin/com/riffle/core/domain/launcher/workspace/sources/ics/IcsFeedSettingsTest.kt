package com.riffle.core.domain.launcher.workspace.sources.ics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsFeedSettingsTest {
    private val secret = "https://calendar.example.com/private/abc-SECRET-token/basic.ics"

    @Test
    fun `default has no feeds`() {
        assertEquals(emptyList(), IcsFeedSettings().feeds)
    }

    @Test
    fun `add normalises and rejects bad urls`() {
        val added = IcsFeedSettings().withAdded("Work", secret) { "id-1" }
        assertEquals("Work", added?.feeds?.single()?.name)
        assertTrue(added?.feeds?.single()?.enabled == true)
        listOf(
            "http://example.com/a.ics",
            "https://user:pw@example.com/a.ics",
            "https://localhost/a.ics",
            "https://192.168.1.4/a.ics",
            "https://nodots/a.ics",
            "ftp://example.com/a.ics",
            "not a url",
            "",
        ).forEach { assertNull(IcsFeedSettings().withAdded("x", it), it) }
    }

    @Test
    fun `webcal is read as https`() {
        val added = IcsFeedSettings().withAdded("", "webcal://calendar.example.com/a.ics")
        assertEquals("https://calendar.example.com/a.ics", added?.feeds?.single()?.url?.value)
        assertEquals("calendar.example.com", added?.feeds?.single()?.name)
    }

    @Test
    fun `duplicates and the feed limit are refused`() {
        val one = assertNotNull(IcsFeedSettings().withAdded("A", secret))
        assertNull(one.withAdded("B", secret))
        var settings = IcsFeedSettings()
        repeat(IcsFeedSettings.MAX_ICS_FEEDS) {
            settings = assertNotNull(settings.withAdded("n", "https://e$it.example.com/a.ics"))
        }
        assertNull(settings.withAdded("n", "https://extra.example.com/a.ics"))
    }

    @Test
    fun `names are cleaned and cut`() {
        val added = IcsFeedSettings().withAdded("  a\u0000b \n c " + "z".repeat(100), secret)
        val name = added?.feeds?.single()?.name.orEmpty()
        assertTrue(name.length <= IcsFeedSettings.MAX_NAME_LENGTH)
        assertTrue(name.startsWith("ab c"))
    }

    @Test
    fun `remove and enable`() {
        val settings = assertNotNull(IcsFeedSettings().withAdded("A", secret) { "a" })
        val off = settings.withEnabled(IcsFeedId("a"), false)
        assertEquals(emptyList(), off.enabledFeeds)
        assertEquals(emptyList(), off.withoutFeed(IcsFeedId("a")).feeds)
    }

    @Test
    fun `only the host is ever shown and toString never carries the url`() {
        val feed = assertNotNull(IcsFeedSettings().withAdded("A", secret)).feeds.single()
        assertEquals("calendar.example.com", feed.host)
        assertFalse(feed.toString().contains("SECRET"))
        assertFalse(IcsFeedSettings(listOf(feed)).toString().contains("SECRET"))
    }
}

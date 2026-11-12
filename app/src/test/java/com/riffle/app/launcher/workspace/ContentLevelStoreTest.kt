package com.riffle.app.launcher.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored form of the per-source content levels (Settings > Privacy). */
class ContentLevelStoreTest {
    @Test
    fun storedEntriesRoundTripAndMalformedOnesAreDropped() {
        val levels = mapOf("notifications" to "hide_text", "apps.all" to "show")
        assertEquals(levels, decodeContentLevelEntries(encodeContentLevelEntries(levels)))
        assertEquals(
            mapOf("media" to "hide_body"),
            decodeContentLevelEntries(setOf("media=hide_body", "=hide_text", "calendar=", "nonsense")),
        )
    }
}

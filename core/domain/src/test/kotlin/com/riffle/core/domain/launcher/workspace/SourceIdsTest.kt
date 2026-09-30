package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceIdsTest {
    @Test
    fun `stored id strings are stable`() {
        assertEquals(
            listOf("apps.all", "apps.recent", "notifications", "shortcuts", "media", "calendar", "rss", "search"),
            SourceIds.BUILT_IN.map { it.value },
        )
    }

    @Test
    fun `built in ids are unique`() {
        assertEquals(SourceIds.BUILT_IN.size, SourceIds.BUILT_IN.toSet().size)
    }
}

package com.riffle.core.domain.launcher.dockpull

import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullEntryTest {
    private val entries =
        listOf(
            DockPullEntryExtent("live:chat:personal", 300f, 348f),
            DockPullEntryExtent("live:mail:personal", 352f, 400f),
            DockPullEntryExtent("ctx:now", 404f, 452f),
        )

    @Test
    fun aTouchOnAnEntryFindsThatEntryAndOneInTheGapOrElsewhereFindsNone() {
        assertEquals("live:chat:personal", dockPullEntryAt(320f, entries))
        assertEquals("live:mail:personal", dockPullEntryAt(352f, entries))
        assertEquals("ctx:now", dockPullEntryAt(420f, entries))
        assertEquals(null, dockPullEntryAt(350f, entries))
        assertEquals(null, dockPullEntryAt(100f, entries))
        assertEquals(null, dockPullEntryAt(452f, entries))
    }
}

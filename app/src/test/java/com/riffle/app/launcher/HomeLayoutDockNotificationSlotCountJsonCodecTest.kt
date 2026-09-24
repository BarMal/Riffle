package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutDockNotificationSlotCountJsonCodecTest {
    @Test
    fun roundTripsDockNotificationSlotCount() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(notificationSlotCount = 2))
            }

        val decoded = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(2, decoded.dock.notificationSlotCount)
    }

    @Test
    fun defaultsDockNotificationSlotCountWhenLegacyJsonOmitsTheField() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(notificationSlotCount = 2))
            }
        val legacyJson =
            encodeHomeLayoutObject(layout).apply { getJSONObject("dock").remove("notificationSlotCount") }

        val decoded = decodeHomeLayout(legacyJson.toString())

        assertEquals(3, decoded.dock.notificationSlotCount)
    }
}

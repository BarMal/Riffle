package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutDockDynamicSectionReservedSlotsJsonCodecTest {
    @Test
    fun roundTripsDockDynamicSectionReservedSlotCount() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(dynamicSectionReservedSlotCount = 3))
            }

        val decoded = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(3, decoded.dock.dynamicSectionReservedSlotCount)
    }

    @Test
    fun defaultsDockDynamicSectionReservedSlotCountWhenLegacyJsonOmitsTheField() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(dynamicSectionReservedSlotCount = 3))
            }
        val legacyJson =
            encodeHomeLayoutObject(layout).apply { getJSONObject("dock").remove("dynamicSectionReservedSlotCount") }

        val decoded = decodeHomeLayout(legacyJson.toString())

        assertEquals(1, decoded.dock.dynamicSectionReservedSlotCount)
    }
}

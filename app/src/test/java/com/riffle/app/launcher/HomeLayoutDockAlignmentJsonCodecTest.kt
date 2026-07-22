package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutDockAlignmentJsonCodecTest {
    @Test
    fun roundTripsNonDefaultDockAlignment() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(alignment = DockAlignment.END))
            }

        val decoded = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(DockAlignment.END, decoded.dock.alignment)
    }

    @Test
    fun defaultsDockAlignmentWhenLegacyJsonOmitsTheField() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(alignment = DockAlignment.END))
            }
        val legacyJson = encodeHomeLayoutObject(layout).apply { getJSONObject("dock").remove("alignment") }

        val decoded = decodeHomeLayout(legacyJson.toString())

        assertEquals(DockAlignment.CENTER, decoded.dock.alignment)
    }
}

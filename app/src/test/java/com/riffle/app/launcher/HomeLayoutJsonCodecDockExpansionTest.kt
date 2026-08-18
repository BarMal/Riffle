package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Persistence for the dock's expansion settings, which live on the dock and are therefore already
 * per home layout.
 */
class HomeLayoutJsonCodecDockExpansionTest {
    @Test
    fun roundTripsDockExpandability() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(isExpandable = false),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(false, decodedLayout.dock.isExpandable)
    }

    @Test
    fun roundTripsDockExpandAffordance() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(expandAffordance = DockExpandAffordance.BUTTON),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(DockExpandAffordance.BUTTON, decodedLayout.dock.expandAffordance)
    }

    @Test
    fun aLayoutWrittenBeforeTheseSettingsExistedStillExpandsBySwipe() {
        // Absent keys must not silently turn expansion off or move it to a button on upgrade.
        val encoded = JSONObject(encodeHomeLayout(HomeLayoutDefaults.standard()))
        encoded.getJSONObject("dock").remove("isExpandable")
        encoded.getJSONObject("dock").remove("expandAffordance")

        val decodedLayout = decodeHomeLayout(encoded.toString())

        assertEquals(true, decodedLayout.dock.isExpandable)
        assertEquals(DockExpandAffordance.GESTURE, decodedLayout.dock.expandAffordance)
    }
}

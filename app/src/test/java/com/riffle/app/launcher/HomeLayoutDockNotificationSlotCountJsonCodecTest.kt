package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dock's live app slots, and the earlier card settings they replace (P5): old stored docks still decode. */
class HomeLayoutDockNotificationSlotCountJsonCodecTest {
    @Test
    fun roundTripsDockLiveAppSlots() {
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(dock = defaults.dock.copy(liveAppSlots = 2))
            }

        val decoded = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(2, decoded.dock.liveAppSlots)
    }

    @Test
    fun defaultsDockLiveAppSlotsWhenStoredJsonNamesNeitherSetting() {
        val layout = HomeLayoutDefaults.standard()
        val json = encodeHomeLayoutObject(layout).apply { getJSONObject("dock").remove("liveAppSlots") }

        assertEquals(DEFAULT_DOCK_LIVE_APP_SLOTS, decodeHomeLayout(json.toString()).dock.liveAppSlots)
    }

    @Test
    fun aDockStoredWithCardsOnKeepsItsSlotCountAsLiveAppSlots() {
        val json =
            encodeHomeLayoutObject(HomeLayoutDefaults.standard()).apply {
                getJSONObject("dock").remove("liveAppSlots")
                getJSONObject("dock").put("showNotificationCards", true).put("notificationSlotCount", 4)
            }

        assertEquals(4, decodeHomeLayout(json.toString()).dock.liveAppSlots)
    }

    @Test
    fun aDockStoredWithCardsOffKeepsTheChoiceOfNoLiveApps() {
        val json =
            encodeHomeLayoutObject(HomeLayoutDefaults.standard()).apply {
                getJSONObject("dock").remove("liveAppSlots")
                getJSONObject("dock").put("showNotificationCards", false).put("notificationSlotCount", 4)
            }

        assertEquals(0, decodeHomeLayout(json.toString()).dock.liveAppSlots)
    }

    @Test
    fun anOutOfRangeStoredValueIsClamped() {
        val json =
            encodeHomeLayoutObject(HomeLayoutDefaults.standard()).apply {
                getJSONObject("dock").put("liveAppSlots", 99)
            }

        assertEquals(5, decodeHomeLayout(json.toString()).dock.liveAppSlots)
    }
}

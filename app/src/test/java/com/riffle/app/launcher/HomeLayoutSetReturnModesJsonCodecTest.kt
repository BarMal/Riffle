package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The leaving-Cards return modes survive a save, and data written before they were tracked decodes
 * as having none -- which is where leaving Cards always went anyway.
 */
class HomeLayoutSetReturnModesJsonCodecTest {
    @Test
    fun roundTripsTheReturnModes() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                .selectMode(LauncherViewMode.CARD_INTERFACE)

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        assertEquals(
            mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.HOME_SCREEN_LIBRARY),
            decoded.lastNonCardsModeByDeviceClass,
        )
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, decoded.modeLeavingCards())
    }

    @Test
    fun decodesDataWrittenBeforeReturnModesWereTrackedAsHavingNone() {
        // No lastNonCardsModes key at all; it must decode rather than fail, and leaving Cards from
        // it falls back to Standard exactly as it did before the field existed.
        val encoded =
            JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard()))
                .apply { remove("lastNonCardsModes") }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(
            emptyMap<HomeLayoutDeviceClass, LauncherViewMode>(),
            decoded.lastNonCardsModeByDeviceClass,
        )
    }
}

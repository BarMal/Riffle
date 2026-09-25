package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeRing
import com.riffle.core.domain.launcher.home.activeModeRing
import com.riffle.core.domain.launcher.home.previousMode
import com.riffle.core.domain.launcher.home.withModeRing
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mode rings (#1225) survive a save, and a set written before rings existed is migrated from
 * the modes it did remember: its preferred mode and where leaving Cards returned to.
 */
class HomeLayoutSetModeRingsJsonCodecTest {
    @Test
    fun roundTripsTheModeRings() {
        val ring =
            ModeRing(
                listOf(
                    LauncherViewMode.CARD_INTERFACE,
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                ),
            )
        val layoutSet =
            HomeLayoutSet.standard()
                .withModeRing(HomeLayoutDeviceClass.PHONE, ring)
                .withModeRing(HomeLayoutDeviceClass.FOLDABLE, ModeRing.DEFAULT)

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        assertEquals(layoutSet.modeRingsByDeviceClass, decoded.modeRingsByDeviceClass)
        assertEquals(ring, decoded.activeModeRing)
    }

    @Test
    fun migratesASetWrittenBeforeRingsFromItsReturnMode() {
        // In Cards, having entered it from Library: the pre-ring encoding of that state.
        val encoded =
            JSONObject(
                encodeHomeLayoutSet(
                    HomeLayoutSet.standard()
                        .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                        .selectMode(LauncherViewMode.CARD_INTERFACE),
                ),
            ).apply {
                remove("modeRings")
                put(
                    "lastNonCardsModes",
                    JSONArray().put(
                        JSONObject()
                            .put("deviceClass", HomeLayoutDeviceClass.PHONE.name)
                            .put("viewMode", LauncherViewMode.HOME_SCREEN_LIBRARY.name),
                    ),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(mapOf(HomeLayoutDeviceClass.PHONE to ModeRing.DEFAULT), decoded.modeRingsByDeviceClass)
        // Leaving Cards still returns where it did before the migration.
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, decoded.previousMode())
    }

    @Test
    fun dropsAStoredRingThatIsNotTwoToThreeDistinctModes() {
        val encoded =
            JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard())).apply {
                put(
                    "modeRings",
                    JSONArray().put(
                        JSONObject()
                            .put("deviceClass", HomeLayoutDeviceClass.PHONE.name)
                            .put("modes", JSONArray().put(LauncherViewMode.CARD_INTERFACE.name)),
                    ),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        // Standard is on screen, so the fallback ring holds it.
        assertEquals(
            listOf(
                LauncherViewMode.STANDARD_APP_DRAWER,
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                LauncherViewMode.CARD_INTERFACE,
            ),
            decoded.activeModeRing.modes,
        )
    }
}

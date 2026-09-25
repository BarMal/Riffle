package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModePair
import com.riffle.core.domain.launcher.home.activeModePair
import com.riffle.core.domain.launcher.home.withHomeMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Home ↔ Library pairs (#1241) survive a save, and a set written with the mode rings of #1225,
 * or before rings existed, is migrated to them.
 */
class HomeLayoutSetModePairsJsonCodecTest {
    @Test
    fun roundTripsTheModePairs() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                .withHomeMode(HomeLayoutDeviceClass.PHONE, LauncherViewMode.CARD_INTERFACE)
                .withHomeMode(HomeLayoutDeviceClass.FOLDABLE, LauncherViewMode.STANDARD_APP_DRAWER)

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        assertEquals(layoutSet.modePairsByDeviceClass, decoded.modePairsByDeviceClass)
        assertEquals(ModePair(LauncherViewMode.CARD_INTERFACE), decoded.activeModePair)
    }

    @Test
    fun roundTripsASetWithNoRecordedPairsUnchanged() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(layoutSet, decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet)))
    }

    @Test
    fun noLongerWritesModeRings() {
        val encoded = JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard()))

        assertFalse(encoded.has("modeRings"))
    }

    @Test
    fun migratesAStoredModeRingOnLibraryToItsFirstHomeMode() {
        val encoded =
            libraryPhoneJson().apply {
                put(
                    "modeRings",
                    JSONArray().put(
                        ringEntry(
                            HomeLayoutDeviceClass.PHONE,
                            LauncherViewMode.HOME_SCREEN_LIBRARY,
                            LauncherViewMode.STANDARD_APP_DRAWER,
                            LauncherViewMode.CARD_INTERFACE,
                        ),
                    ),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, decoded.activeKey.viewMode)
        assertEquals(ModePair(LauncherViewMode.STANDARD_APP_DRAWER), decoded.activeModePair)
    }

    @Test
    fun migratesAStoredModeRingOnAHomeModeToThatMode() {
        val encoded =
            JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard().selectMode(LauncherViewMode.CARD_INTERFACE)))
                .apply {
                    remove(MODE_PAIRS_KEY)
                    put(
                        "modeRings",
                        JSONArray().put(
                            ringEntry(
                                HomeLayoutDeviceClass.PHONE,
                                LauncherViewMode.STANDARD_APP_DRAWER,
                                LauncherViewMode.CARD_INTERFACE,
                            ),
                        ),
                    )
                }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(ModePair(LauncherViewMode.CARD_INTERFACE), decoded.activeModePair)
    }

    @Test
    fun migratesARingWithOnlyUnknownModesToTheDefaultPair() {
        val encoded =
            libraryPhoneJson().apply {
                put(
                    "modeRings",
                    JSONArray().put(
                        JSONObject()
                            .put("deviceClass", HomeLayoutDeviceClass.PHONE.name)
                            .put("modes", JSONArray().put("NOT_A_MODE")),
                    ),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(ModePair.DEFAULT, decoded.activeModePair)
    }

    @Test
    fun migratesASetWrittenBeforeRingsFromItsReturnMode() {
        val encoded =
            libraryPhoneJson().apply {
                put(
                    "lastNonCardsModes",
                    JSONArray().put(
                        JSONObject()
                            .put("deviceClass", HomeLayoutDeviceClass.PHONE.name)
                            .put("viewMode", LauncherViewMode.STANDARD_APP_DRAWER.name),
                    ),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(ModePair(LauncherViewMode.STANDARD_APP_DRAWER), decoded.activeModePair)
    }

    @Test
    fun dropsAStoredPairWhoseHomeIsLibraryOrUnknown() {
        val encoded =
            libraryPhoneJson().apply {
                put(
                    MODE_PAIRS_KEY,
                    JSONArray()
                        .put(pairEntry(HomeLayoutDeviceClass.PHONE, LauncherViewMode.HOME_SCREEN_LIBRARY.name))
                        .put(pairEntry(HomeLayoutDeviceClass.FOLDABLE, "NOT_A_MODE")),
                )
            }

        val decoded = decodeHomeLayoutSet(encoded.toString())

        assertEquals(emptyMap<HomeLayoutDeviceClass, ModePair>(), decoded.modePairsByDeviceClass)
        assertEquals(ModePair.DEFAULT, decoded.activeModePair)
    }

    /** A phone on Library with neither pairs nor rings stored. */
    private fun libraryPhoneJson(): JSONObject =
        JSONObject(
            encodeHomeLayoutSet(
                HomeLayoutSet.standard()
                    .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                    .copy(modePairsByDeviceClass = emptyMap()),
            ),
        ).apply { remove(MODE_PAIRS_KEY) }

    private fun ringEntry(
        deviceClass: HomeLayoutDeviceClass,
        vararg modes: LauncherViewMode,
    ): JSONObject =
        JSONObject()
            .put("deviceClass", deviceClass.name)
            .put("modes", JSONArray(modes.map(LauncherViewMode::name)))

    private fun pairEntry(
        deviceClass: HomeLayoutDeviceClass,
        home: String,
    ): JSONObject =
        JSONObject()
            .put("deviceClass", deviceClass.name)
            .put("home", home)
}

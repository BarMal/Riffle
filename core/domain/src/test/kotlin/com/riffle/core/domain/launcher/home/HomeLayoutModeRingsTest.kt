package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.home.LauncherViewMode.CARD_INTERFACE
import com.riffle.core.domain.launcher.home.LauncherViewMode.HOME_SCREEN_LIBRARY
import com.riffle.core.domain.launcher.home.LauncherViewMode.STANDARD_APP_DRAWER
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutModeRingsTest {
    @Test
    fun aSetWrittenBeforeRingsIsMigratedFromItsPreferredAndLastNonCardsModes() {
        val decoded =
            legacySet(
                active = HomeLayoutKey(CARD_INTERFACE),
                preferredModes =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to CARD_INTERFACE,
                        HomeLayoutDeviceClass.FOLDABLE to STANDARD_APP_DRAWER,
                    ),
            ).withRestoredModeRings(
                storedRings = null,
                legacyLastNonCardsModes = mapOf(HomeLayoutDeviceClass.PHONE to STANDARD_APP_DRAWER),
            )

        assertEquals(
            mapOf(
                HomeLayoutDeviceClass.PHONE to ModeRing(listOf(STANDARD_APP_DRAWER, CARD_INTERFACE)),
                HomeLayoutDeviceClass.FOLDABLE to
                    ModeRing(listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY, CARD_INTERFACE)),
            ),
            decoded.modeRingsByDeviceClass,
        )
        // Leaving Cards still goes where it went before the migration.
        assertEquals(STANDARD_APP_DRAWER, decoded.previousMode())
    }

    @Test
    fun aLegacySetWithNoReturnModeGetsTheDefaultRingWhenItHoldsTheActiveMode() {
        val decoded =
            legacySet(active = HomeLayoutKey(HOME_SCREEN_LIBRARY))
                .withRestoredModeRings(storedRings = null)

        assertEquals(ModeRing.DEFAULT, decoded.activeModeRing)
        assertEquals(0, decoded.activeModeIndex)
    }

    @Test
    fun storedRingsAreKeptAsTheyAre() {
        val ring = ModeRing(listOf(CARD_INTERFACE, HOME_SCREEN_LIBRARY))
        val decoded =
            legacySet(active = HomeLayoutKey(HOME_SCREEN_LIBRARY))
                .withRestoredModeRings(
                    storedRings = mapOf(HomeLayoutDeviceClass.PHONE to ring),
                    legacyLastNonCardsModes = mapOf(HomeLayoutDeviceClass.PHONE to STANDARD_APP_DRAWER),
                )

        assertEquals(mapOf(HomeLayoutDeviceClass.PHONE to ring), decoded.modeRingsByDeviceClass)
    }

    @Test
    fun aStoredRingMissingItsDeviceClassModeIsRepaired() {
        val decoded =
            legacySet(active = HomeLayoutKey(STANDARD_APP_DRAWER))
                .withRestoredModeRings(storedRings = mapOf(HomeLayoutDeviceClass.PHONE to ModeRing.DEFAULT))

        assertEquals(
            listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE, STANDARD_APP_DRAWER),
            decoded.activeModeRing.modes,
        )
    }

    private fun legacySet(
        active: HomeLayoutKey,
        preferredModes: Map<HomeLayoutDeviceClass, LauncherViewMode> = mapOf(active.deviceClass to active.viewMode),
    ): HomeLayoutSet =
        HomeLayoutSet(
            activeKey = active,
            layouts = mapOf(active to HomeLayoutSet.defaultLayout(active)),
            preferredModesByDeviceClass = preferredModes,
        )
}

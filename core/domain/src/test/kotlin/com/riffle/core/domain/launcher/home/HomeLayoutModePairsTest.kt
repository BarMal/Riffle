package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.FOLDABLE
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.PHONE
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.TABLET
import com.riffle.core.domain.launcher.home.LauncherViewMode.CARD_INTERFACE
import com.riffle.core.domain.launcher.home.LauncherViewMode.HOME_SCREEN_LIBRARY
import com.riffle.core.domain.launcher.home.LauncherViewMode.STANDARD_APP_DRAWER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutModePairsTest {
    @Test
    fun aFreshSetOnStandardPairsStandardWithLibrary() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(ModePair(STANDARD_APP_DRAWER), layoutSet.activeModePair)
        assertEquals(HOME_SCREEN_LIBRARY, layoutSet.activeModePair.counterpart(layoutSet.activeKey.viewMode))
    }

    @Test
    fun movingToLibraryKeepsTheHomeItCameFrom() {
        val layoutSet = HomeLayoutSet.standard().selectMode(HOME_SCREEN_LIBRARY)

        assertEquals(ModePair(STANDARD_APP_DRAWER), layoutSet.activeModePair)
        assertEquals(STANDARD_APP_DRAWER, layoutSet.activeModePair.counterpart(HOME_SCREEN_LIBRARY))
    }

    @Test
    fun selectingAHomeModeMakesItTheHome() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(CARD_INTERFACE)
                .selectMode(HOME_SCREEN_LIBRARY)

        assertEquals(ModePair(CARD_INTERFACE), layoutSet.activeModePair)
    }

    @Test
    fun theCurrentModeIsAlwaysInItsPairWhateverSelectsIt() {
        val start = HomeLayoutSet.standard().selectMode(HOME_SCREEN_LIBRARY)
        val results =
            listOf(
                start.selectMode(CARD_INTERFACE),
                start.withModeChosenFor(FOLDABLE, CARD_INTERFACE).selectDeviceClass(FOLDABLE),
                start.selectDeviceClass(TABLET),
                start.withHomeMode(PHONE, CARD_INTERFACE).selectMode(STANDARD_APP_DRAWER),
            )

        results.forEach { layoutSet ->
            assertTrue(layoutSet.activeKey.viewMode in layoutSet.activeModePair, layoutSet.toString())
            layoutSet.preferredModesByDeviceClass.forEach { (deviceClass, mode) ->
                assertTrue(mode in layoutSet.modePairFor(deviceClass), "$deviceClass $mode")
            }
        }
    }

    @Test
    fun choosingHomeWhileOnHomeSwitchesToIt() {
        val layoutSet = HomeLayoutSet.standard().withHomeMode(PHONE, CARD_INTERFACE)

        assertEquals(CARD_INTERFACE, layoutSet.activeKey.viewMode)
        assertEquals(ModePair(CARD_INTERFACE), layoutSet.activeModePair)
    }

    @Test
    fun choosingHomeWhileOnLibraryStaysOnLibrary() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(HOME_SCREEN_LIBRARY)
                .withHomeMode(PHONE, CARD_INTERFACE)

        assertEquals(HOME_SCREEN_LIBRARY, layoutSet.activeKey.viewMode)
        assertEquals(CARD_INTERFACE, layoutSet.activeModePair.counterpart(HOME_SCREEN_LIBRARY))
    }

    @Test
    fun choosingHomeForAnotherDeviceClassLeavesTheScreenAlone() {
        val layoutSet =
            HomeLayoutSet.standard()
                .withModeChosenFor(FOLDABLE, STANDARD_APP_DRAWER)
                .withHomeMode(FOLDABLE, CARD_INTERFACE)

        assertEquals(HomeLayoutKey(STANDARD_APP_DRAWER), layoutSet.activeKey)
        assertEquals(CARD_INTERFACE, layoutSet.preferredModesByDeviceClass[FOLDABLE])
        assertEquals(ModePair(CARD_INTERFACE), layoutSet.modePairFor(FOLDABLE))
    }

    @Test
    fun libraryCannotBeChosenAsHome() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(layoutSet, layoutSet.withHomeMode(PHONE, HOME_SCREEN_LIBRARY))
    }

    @Test
    fun restoringStoredPairsGivesBackAnEqualSet() {
        val fresh = HomeLayoutSet.standard()
        val configured =
            fresh
                .selectMode(HOME_SCREEN_LIBRARY)
                .withHomeMode(FOLDABLE, CARD_INTERFACE)

        assertEquals(fresh, fresh.withRestoredModePairs(storedPairs = fresh.modePairsByDeviceClass))
        assertEquals(configured, configured.withRestoredModePairs(storedPairs = configured.modePairsByDeviceClass))
    }

    @Test
    fun aStoredPairDisagreeingWithTheModeOnScreenFollowsTheScreen() {
        val decoded =
            storedSet(active = HomeLayoutKey(STANDARD_APP_DRAWER))
                .withRestoredModePairs(storedPairs = mapOf(PHONE to ModePair(CARD_INTERFACE)))

        assertEquals(ModePair(STANDARD_APP_DRAWER), decoded.activeModePair)
    }

    @Test
    fun storedPairsWinOverLegacyRingsAndReturnModes() {
        val decoded =
            storedSet(active = HomeLayoutKey(HOME_SCREEN_LIBRARY))
                .withRestoredModePairs(
                    storedPairs = mapOf(PHONE to ModePair(CARD_INTERFACE)),
                    legacyRings = mapOf(PHONE to listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY)),
                    legacyLastNonCardsModes = mapOf(PHONE to STANDARD_APP_DRAWER),
                )

        assertEquals(mapOf(PHONE to ModePair(CARD_INTERFACE)), decoded.modePairsByDeviceClass)
    }

    @Test
    fun aRingSetOnLibraryMigratesToTheRingsFirstHomeMode() {
        val decoded =
            storedSet(
                active = HomeLayoutKey(HOME_SCREEN_LIBRARY),
                preferredModes = mapOf(PHONE to HOME_SCREEN_LIBRARY, FOLDABLE to CARD_INTERFACE),
            ).withRestoredModePairs(
                storedPairs = null,
                legacyRings =
                    mapOf(
                        PHONE to listOf(HOME_SCREEN_LIBRARY, STANDARD_APP_DRAWER, CARD_INTERFACE),
                        FOLDABLE to listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY, CARD_INTERFACE),
                    ),
            )

        assertEquals(STANDARD_APP_DRAWER, decoded.activeModePair.counterpart(HOME_SCREEN_LIBRARY))
        // The foldable shows Cards, so Cards stays its Home whatever the ring's order.
        assertEquals(ModePair(CARD_INTERFACE), decoded.modePairFor(FOLDABLE))
    }

    @Test
    fun aDeviceClassWithoutAStoredRingKeepsItsFallback() {
        val decoded =
            storedSet(active = HomeLayoutKey(HOME_SCREEN_LIBRARY))
                .withRestoredModePairs(storedPairs = null, legacyRings = emptyMap())

        assertEquals(emptyMap(), decoded.modePairsByDeviceClass)
        assertEquals(ModePair.DEFAULT, decoded.activeModePair)
    }

    @Test
    fun aSetWrittenBeforeRingsMigratesFromItsPreferredAndLastNonCardsModes() {
        val decoded =
            storedSet(
                active = HomeLayoutKey(HOME_SCREEN_LIBRARY),
                preferredModes = mapOf(PHONE to HOME_SCREEN_LIBRARY, FOLDABLE to CARD_INTERFACE),
            ).withRestoredModePairs(
                storedPairs = null,
                legacyLastNonCardsModes = mapOf(PHONE to STANDARD_APP_DRAWER, TABLET to STANDARD_APP_DRAWER),
            )

        assertEquals(
            mapOf(
                PHONE to ModePair(STANDARD_APP_DRAWER),
                FOLDABLE to ModePair(CARD_INTERFACE),
                TABLET to ModePair(STANDARD_APP_DRAWER),
            ),
            decoded.modePairsByDeviceClass,
        )
    }

    private fun storedSet(
        active: HomeLayoutKey,
        preferredModes: Map<HomeLayoutDeviceClass, LauncherViewMode> = mapOf(active.deviceClass to active.viewMode),
    ): HomeLayoutSet =
        HomeLayoutSet(
            activeKey = active,
            layouts = mapOf(active to HomeLayoutSet.defaultLayout(active)),
            preferredModesByDeviceClass = preferredModes,
        )
}

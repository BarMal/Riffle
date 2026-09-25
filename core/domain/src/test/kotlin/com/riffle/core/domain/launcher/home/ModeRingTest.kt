package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.home.LauncherViewMode.CARD_INTERFACE
import com.riffle.core.domain.launcher.home.LauncherViewMode.HOME_SCREEN_LIBRARY
import com.riffle.core.domain.launcher.home.LauncherViewMode.STANDARD_APP_DRAWER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModeRingTest {
    private val all = ModeRing(listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY, CARD_INTERFACE))

    @Test
    fun theDefaultRingIsLibraryThenCards() {
        assertEquals(listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE), ModeRing.DEFAULT.modes)
    }

    @Test
    fun aRingHoldsTwoOrThreeDistinctModes() {
        assertFailsWith<IllegalArgumentException> { ModeRing(listOf(CARD_INTERFACE)) }
        assertFailsWith<IllegalArgumentException> { ModeRing(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ModeRing(listOf(CARD_INTERFACE, CARD_INTERFACE)) }
        assertFailsWith<IllegalArgumentException> {
            ModeRing(listOf(CARD_INTERFACE, HOME_SCREEN_LIBRARY, CARD_INTERFACE))
        }
        assertNull(ModeRing.of(listOf(HOME_SCREEN_LIBRARY)))
        assertNull(ModeRing.of(listOf(HOME_SCREEN_LIBRARY, HOME_SCREEN_LIBRARY)))
        assertEquals(ModeRing.DEFAULT, ModeRing.of(listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE)))
    }

    @Test
    fun nextAndPreviousWrapAroundTheRing() {
        assertEquals(HOME_SCREEN_LIBRARY, all.next(STANDARD_APP_DRAWER))
        assertEquals(STANDARD_APP_DRAWER, all.next(CARD_INTERFACE))
        assertEquals(CARD_INTERFACE, all.previous(STANDARD_APP_DRAWER))
        assertEquals(HOME_SCREEN_LIBRARY, all.previous(CARD_INTERFACE))

        // With two modes each is the other's next and previous.
        assertEquals(CARD_INTERFACE, ModeRing.DEFAULT.next(HOME_SCREEN_LIBRARY))
        assertEquals(CARD_INTERFACE, ModeRing.DEFAULT.previous(HOME_SCREEN_LIBRARY))
    }

    @Test
    fun steppingFromAModeOutsideTheRingLandsOnItsEnds() {
        assertEquals(HOME_SCREEN_LIBRARY, ModeRing.DEFAULT.next(STANDARD_APP_DRAWER))
        assertEquals(CARD_INTERFACE, ModeRing.DEFAULT.previous(STANDARD_APP_DRAWER))
        assertEquals(-1, ModeRing.DEFAULT.indexOf(STANDARD_APP_DRAWER))
    }

    @Test
    fun enablingAndDisablingKeepsTwoToThreeModes() {
        assertEquals(
            listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE, STANDARD_APP_DRAWER),
            ModeRing.DEFAULT.withModeEnabled(STANDARD_APP_DRAWER, enabled = true).modes,
        )
        assertEquals(
            listOf(STANDARD_APP_DRAWER, CARD_INTERFACE),
            all.withModeEnabled(HOME_SCREEN_LIBRARY, enabled = false).modes,
        )
        // Refused: it would leave one mode.
        assertEquals(ModeRing.DEFAULT, ModeRing.DEFAULT.withModeEnabled(CARD_INTERFACE, enabled = false))
        // No-ops.
        assertEquals(ModeRing.DEFAULT, ModeRing.DEFAULT.withModeEnabled(CARD_INTERFACE, enabled = true))
        assertEquals(ModeRing.DEFAULT, ModeRing.DEFAULT.withModeEnabled(STANDARD_APP_DRAWER, enabled = false))
    }

    @Test
    fun movingAModeClampsToTheEnds() {
        assertEquals(
            listOf(HOME_SCREEN_LIBRARY, STANDARD_APP_DRAWER, CARD_INTERFACE),
            all.withModeMoved(STANDARD_APP_DRAWER, offset = 1).modes,
        )
        assertEquals(
            listOf(CARD_INTERFACE, STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY),
            all.withModeMoved(CARD_INTERFACE, offset = -5).modes,
        )
        assertEquals(all, all.withModeMoved(STANDARD_APP_DRAWER, offset = -1))
        assertEquals(ModeRing.DEFAULT, ModeRing.DEFAULT.withModeMoved(STANDARD_APP_DRAWER, offset = 1))
    }

    @Test
    fun includingAModeInsertsItAfterTheGivenMode() {
        assertEquals(
            listOf(HOME_SCREEN_LIBRARY, STANDARD_APP_DRAWER, CARD_INTERFACE),
            ModeRing.DEFAULT.including(STANDARD_APP_DRAWER, after = HOME_SCREEN_LIBRARY).modes,
        )
        assertEquals(
            listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE, STANDARD_APP_DRAWER),
            ModeRing.DEFAULT.including(STANDARD_APP_DRAWER).modes,
        )
        assertEquals(ModeRing.DEFAULT, ModeRing.DEFAULT.including(CARD_INTERFACE, after = HOME_SCREEN_LIBRARY))
    }

    @Test
    fun theNeighbourOfARemovedModeIsTheOneAfterItOrBeforeItWhenItWasLast() {
        val withoutLibrary = all.withModeEnabled(HOME_SCREEN_LIBRARY, enabled = false)
        val withoutCards = all.withModeEnabled(CARD_INTERFACE, enabled = false)
        val withoutStandard = all.withModeEnabled(STANDARD_APP_DRAWER, enabled = false)

        assertEquals(CARD_INTERFACE, all.neighbourAfterRemoving(HOME_SCREEN_LIBRARY, withoutLibrary))
        assertEquals(HOME_SCREEN_LIBRARY, all.neighbourAfterRemoving(CARD_INTERFACE, withoutCards))
        assertEquals(HOME_SCREEN_LIBRARY, all.neighbourAfterRemoving(STANDARD_APP_DRAWER, withoutStandard))
    }

    @Test
    fun migrationPutsNonCardsModesFirstAndDeduplicates() {
        // In Cards, having entered it from Library.
        assertEquals(ModeRing.DEFAULT, ModeRing.migrated(CARD_INTERFACE, HOME_SCREEN_LIBRARY))
        // In Cards, having entered it from Standard.
        assertEquals(
            listOf(STANDARD_APP_DRAWER, CARD_INTERFACE),
            ModeRing.migrated(CARD_INTERFACE, STANDARD_APP_DRAWER).modes,
        )
        // On Standard, having been to Library before.
        assertEquals(
            listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY),
            ModeRing.migrated(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY).modes,
        )
    }

    @Test
    fun migrationFallsBackToTheDefaultRingHoldingThePreferredMode() {
        assertEquals(ModeRing.DEFAULT, ModeRing.migrated(HOME_SCREEN_LIBRARY, HOME_SCREEN_LIBRARY))
        assertEquals(ModeRing.DEFAULT, ModeRing.migrated(CARD_INTERFACE, null))
        assertEquals(ModeRing.DEFAULT, ModeRing.migrated(null, null))
        assertEquals(all, ModeRing.migrated(STANDARD_APP_DRAWER, STANDARD_APP_DRAWER))
        assertEquals(all, ModeRing.migrated(STANDARD_APP_DRAWER, null))
    }

    @Test
    fun migrationCoversEveryDeviceClassEitherLegacyMapMentions() {
        val rings =
            ModeRing.migratedByDeviceClass(
                preferredModes =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to CARD_INTERFACE,
                        HomeLayoutDeviceClass.TABLET to STANDARD_APP_DRAWER,
                    ),
                lastNonCardsModes = mapOf(HomeLayoutDeviceClass.FOLDABLE to HOME_SCREEN_LIBRARY),
            )

        assertEquals(
            mapOf(
                HomeLayoutDeviceClass.PHONE to ModeRing.DEFAULT,
                HomeLayoutDeviceClass.TABLET to all,
                HomeLayoutDeviceClass.FOLDABLE to ModeRing.DEFAULT,
            ),
            rings,
        )
    }
}

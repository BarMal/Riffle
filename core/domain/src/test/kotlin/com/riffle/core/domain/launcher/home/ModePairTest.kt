package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.home.LauncherViewMode.CARD_INTERFACE
import com.riffle.core.domain.launcher.home.LauncherViewMode.HOME_SCREEN_LIBRARY
import com.riffle.core.domain.launcher.home.LauncherViewMode.STANDARD_APP_DRAWER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModePairTest {
    @Test
    fun theDefaultPairIsCardsAndLibrary() {
        assertEquals(CARD_INTERFACE, ModePair.DEFAULT.home)
        assertEquals(HOME_SCREEN_LIBRARY, ModePair.DEFAULT.library)
        assertEquals(CARD_INTERFACE, ModePair.DEFAULT.modeFor(ModeSurface.HOME))
        assertEquals(HOME_SCREEN_LIBRARY, ModePair.DEFAULT.modeFor(ModeSurface.LIBRARY))
    }

    @Test
    fun libraryIsNeverAHome() {
        assertFailsWith<IllegalArgumentException> { ModePair(HOME_SCREEN_LIBRARY) }
        assertNull(ModePair.of(HOME_SCREEN_LIBRARY))
        assertNull(ModePair.of(null))
        assertEquals(ModePair(STANDARD_APP_DRAWER), ModePair.of(STANDARD_APP_DRAWER))
    }

    @Test
    fun counterpartLeadsFromEitherSideToTheOther() {
        val cards = ModePair(CARD_INTERFACE)
        val standard = ModePair(STANDARD_APP_DRAWER)

        assertEquals(HOME_SCREEN_LIBRARY, cards.counterpart(CARD_INTERFACE))
        assertEquals(CARD_INTERFACE, cards.counterpart(HOME_SCREEN_LIBRARY))
        assertEquals(HOME_SCREEN_LIBRARY, standard.counterpart(STANDARD_APP_DRAWER))
        assertEquals(STANDARD_APP_DRAWER, standard.counterpart(HOME_SCREEN_LIBRARY))
    }

    @Test
    fun aHomeModeOutsideThePairStillLeadsToLibrary() {
        assertEquals(HOME_SCREEN_LIBRARY, ModePair(CARD_INTERFACE).counterpart(STANDARD_APP_DRAWER))
    }

    @Test
    fun aPairHoldsExactlyItsHomeAndLibrary() {
        val pair = ModePair(STANDARD_APP_DRAWER)

        assertTrue(STANDARD_APP_DRAWER in pair)
        assertTrue(HOME_SCREEN_LIBRARY in pair)
        assertFalse(CARD_INTERFACE in pair)
    }

    @Test
    fun homeModesAreCardsThenStandard() {
        assertEquals(listOf(CARD_INTERFACE, STANDARD_APP_DRAWER), ModePair.HOME_MODES)
    }

    @Test
    fun fallbackKeepsAHomeModeAndOtherwiseDefaults() {
        assertEquals(ModePair(STANDARD_APP_DRAWER), ModePair.fallbackFor(STANDARD_APP_DRAWER))
        assertEquals(ModePair.DEFAULT, ModePair.fallbackFor(HOME_SCREEN_LIBRARY))
        assertEquals(ModePair.DEFAULT, ModePair.fallbackFor(null))
    }

    @Test
    fun aRingMigratesToTheModeOnScreenWhenThatIsAHomeMode() {
        val ring = listOf(STANDARD_APP_DRAWER, HOME_SCREEN_LIBRARY, CARD_INTERFACE)

        assertEquals(ModePair(CARD_INTERFACE), ModePair.fromLegacyRing(ring, currentMode = CARD_INTERFACE))
    }

    @Test
    fun aRingOnLibraryMigratesToItsFirstNonLibraryMode() {
        assertEquals(
            ModePair(STANDARD_APP_DRAWER),
            ModePair.fromLegacyRing(
                listOf(HOME_SCREEN_LIBRARY, STANDARD_APP_DRAWER, CARD_INTERFACE),
                currentMode = HOME_SCREEN_LIBRARY,
            ),
        )
        assertEquals(
            ModePair(CARD_INTERFACE),
            ModePair.fromLegacyRing(listOf(HOME_SCREEN_LIBRARY, CARD_INTERFACE), currentMode = null),
        )
    }

    @Test
    fun aRingWithNoHomeModeMigratesToTheDefault() {
        assertEquals(ModePair.DEFAULT, ModePair.fromLegacyRing(listOf(HOME_SCREEN_LIBRARY), HOME_SCREEN_LIBRARY))
        assertEquals(ModePair.DEFAULT, ModePair.fromLegacyRing(emptyList(), currentMode = null))
    }

    @Test
    fun preRingSettingsMigrateToThePreferredThenTheLastNonCardsHomeMode() {
        assertEquals(ModePair(CARD_INTERFACE), ModePair.migrated(CARD_INTERFACE, STANDARD_APP_DRAWER))
        assertEquals(ModePair(STANDARD_APP_DRAWER), ModePair.migrated(HOME_SCREEN_LIBRARY, STANDARD_APP_DRAWER))
        assertEquals(ModePair.DEFAULT, ModePair.migrated(HOME_SCREEN_LIBRARY, HOME_SCREEN_LIBRARY))
        assertEquals(ModePair.DEFAULT, ModePair.migrated(null, null))
    }
}

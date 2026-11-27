package com.riffle.app.launcher.summon

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummonFavouritesRowTest {
    private companion object {
        val FULL = 44.dp
    }

    @Test
    fun aFewFavouritesKeepTheirFullSize() {
        assertEquals(FULL, summonFavouriteIconSize(availableWidth = 360.dp, count = 4, fullSize = FULL))
    }

    @Test
    fun manyFavouritesShrinkSoTheWholeRowFitsTheScreen() {
        val available = 328.dp
        val count = 7
        val size = summonFavouriteIconSize(available, count, FULL)
        assertTrue(size < FULL)
        assertTrue(summonFavouritesRowWidth(size, count) <= available)
    }

    @Test
    fun aSmallChosenSizeIsNeverGrownToFit() {
        assertEquals(32.dp, summonFavouriteIconSize(availableWidth = 400.dp, count = 3, fullSize = 32.dp))
    }

    @Test
    fun noFavouritesNeedsNoRoom() {
        assertEquals(FULL, summonFavouriteIconSize(availableWidth = 0.dp, count = 0, fullSize = FULL))
    }
}

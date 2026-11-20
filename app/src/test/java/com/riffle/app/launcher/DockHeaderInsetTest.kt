package com.riffle.app.launcher

import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Dock's action row stands in from the Dock's sides far enough to clear its rounded corner. */
class DockHeaderInsetTest {
    @Test
    fun aSquareDockKeepsTheUsualPadding() {
        assertEquals(RiffleSpacing.s, DockHeaderInset.forCorner(0.dp))
    }

    @Test
    fun aRoundDockMovesTheRowInWithItsCorner() {
        assertEquals(16.dp, DockHeaderInset.forCorner(40.dp))
        assertTrue(DockHeaderInset.forCorner(28.dp) > DockHeaderInset.forCorner(12.dp))
    }

    @Test
    fun theInsetStopsGrowingSoTheRowKeepsItsRoom() {
        assertEquals(RiffleSpacing.xl, DockHeaderInset.forCorner(200.dp))
    }
}

package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TactileSliderTest {
    @Test
    fun noTickWhenTheSteppedValueHasNotActuallyChanged() {
        assertEquals(
            TactileSliderHapticTick.NONE,
            tactileSliderHapticTick(previousValue = 10, newValue = 10, valueRange = 0..20),
        )
    }

    @Test
    fun boundaryTickAtTheRangeMinimum() {
        assertEquals(
            TactileSliderHapticTick.BOUNDARY,
            tactileSliderHapticTick(previousValue = 1, newValue = 0, valueRange = 0..20),
        )
    }

    @Test
    fun boundaryTickAtTheRangeMaximum() {
        assertEquals(
            TactileSliderHapticTick.BOUNDARY,
            tactileSliderHapticTick(previousValue = 19, newValue = 20, valueRange = 0..20),
        )
    }

    @Test
    fun detentTickForEveryOtherStepCrossed() {
        assertEquals(
            TactileSliderHapticTick.DETENT,
            tactileSliderHapticTick(previousValue = 9, newValue = 10, valueRange = 0..20),
        )
    }

    @Test
    fun boundaryTickWinsOverDetentWhenAStepLandsExactlyOnTheMaximum() {
        // A range whose steps land exactly on both ends: the last crossing should still read as a
        // boundary tick, not a plain detent, even though it is also "one more step".
        assertEquals(
            TactileSliderHapticTick.BOUNDARY,
            tactileSliderHapticTick(previousValue = 15, newValue = 20, valueRange = 0..20),
        )
    }
}

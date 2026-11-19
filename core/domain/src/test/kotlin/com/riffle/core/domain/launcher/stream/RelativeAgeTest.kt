package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeAgeTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun minutesRollOverToHoursAtSixtyAndHoursToDaysAtTwentyFour() {
        assertEquals(RelativeAge.Age(RelativeAge.Unit.MINUTES, 59), RelativeAge.of(59 * minute))
        assertEquals(RelativeAge.Age(RelativeAge.Unit.HOURS, 1), RelativeAge.of(60 * minute))
        assertEquals(RelativeAge.Age(RelativeAge.Unit.HOURS, 3), RelativeAge.of(180 * minute))
        assertEquals(RelativeAge.Age(RelativeAge.Unit.HOURS, 23), RelativeAge.of(23 * hour))
        assertEquals(RelativeAge.Age(RelativeAge.Unit.DAYS, 1), RelativeAge.of(24 * hour))
    }

    @Test
    fun theFutureAndTheJustNowReadAsZeroMinutes() {
        assertEquals(RelativeAge.Age(RelativeAge.Unit.MINUTES, 0), RelativeAge.of(-5 * minute))
        assertEquals(RelativeAge.Age(RelativeAge.Unit.MINUTES, 0), RelativeAge.of(30_000L))
    }
}

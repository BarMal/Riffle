package com.riffle.app.launcher.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Stream's relative times roll over to hours at 60 minutes and to days at 24 hours, through the real formatter (the
 * source card's "Last played" and every notification's subtitle read [SystemStreamFormats.age]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemStreamFormatsAgeTest {
    private val now = 1_800_000_000_000L
    private val formats = SystemStreamFormats(clock = { now })
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun age(elapsed: Long) = formats.age(now - elapsed)

    @Test
    fun anAgeRollsOverToHoursAtSixtyMinutesAndToDaysAtTwentyFourHours() {
        assertEquals("59 min. ago", age(59 * minute))
        assertEquals("1 hr. ago", age(60 * minute))
        assertEquals("3 hr. ago", age(180 * minute))
        assertEquals("23 hr. ago", age(23 * hour))
        assertEquals("Yesterday", age(24 * hour))
    }
}

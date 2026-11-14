package com.riffle.app.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationImageBudgetTest {
    private fun picture(
        key: String,
        posted: Long,
        bytes: Int = 1_000_000,
    ) = NotificationImageBudget.Candidate(key, bytes, isPicture = true, postedAtEpochMillis = posted)

    private fun person(
        key: String,
        posted: Long = 1L,
        bytes: Int = 65_536,
    ) = NotificationImageBudget.Candidate(key, bytes, isPicture = false, postedAtEpochMillis = posted)

    @Test
    fun everythingIsKeptWhileItFits() {
        val kept = NotificationImageBudget.select(listOf(person("p1"), picture("a", 5), picture("b", 9)))

        assertEquals(setOf("p1", "a", "b"), kept)
    }

    @Test
    fun picturesAreCappedInCountAndTheNewestKeepTheirs() {
        val pictures = (1..12).map { picture("pic$it", posted = it.toLong(), bytes = 10_000) }

        val kept = NotificationImageBudget.select(pictures)

        assertEquals(NotificationImageBudget.MAX_PICTURES, kept.size)
        assertTrue("pic12" in kept && "pic5" in kept)
        assertFalse("pic4" in kept)
    }

    @Test
    fun theByteBudgetIsNeverExceededAndContactPhotosComeBeforePictures() {
        val big = NotificationImageBudget.MAX_BYTES - 100_000
        val kept =
            NotificationImageBudget.select(listOf(picture("old", 1, bytes = big), person("p", 99, bytes = 200_000)))

        // The contact photo is taken first; the picture that would then overspend is dropped.
        assertEquals(setOf("p"), kept)
    }

    @Test
    fun nothingIsKeptOfNothing() {
        assertEquals(emptySet<String>(), NotificationImageBudget.select(emptyList()))
    }
}

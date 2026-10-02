package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedBackgroundLabelsTest {
    @Test
    fun everyIntervalHasADistinctChipLabel() {
        val labels = FeedRefreshIntervalOption.entries.map { it.chipLabel() }

        assertEquals(labels.size, labels.toSet().size)
        assertEquals("Off", FeedRefreshIntervalOption.OFF.chipLabel())
    }

    @Test
    fun theOffExplanationStatesNothingRunsInTheBackground() {
        val text = FeedRefreshIntervalOption.OFF.explanation()

        assertTrue(text.contains("Nothing runs in the background"))
    }

    @Test
    fun anEnabledExplanationNamesTheIntervalAndAdmitsAndroidMayDelay() {
        val text = FeedRefreshIntervalOption.HOURS_6.explanation()

        assertTrue(text.contains("6 hours"))
        assertTrue(text.contains("delay"))
    }

    @Test
    fun statusLineHandlesNeverRunAndEveryKind() {
        assertEquals("No background refresh has run yet.", backgroundRefreshStatus(null) { "x" })
        FeedBackgroundRunRecord.Kind.entries.forEach { kind ->
            val line = backgroundRefreshStatus(FeedBackgroundRunRecord(1, kind)) { "2 hours ago" }

            assertTrue(line.startsWith("Last background refresh: 2 hours ago ("))
            assertFalse(line.contains("http"))
        }
    }
}

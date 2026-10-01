package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedRefreshLabelsTest {
    private fun report(vararg outcomes: FeedRefreshOutcome) =
        FeedRefreshReport(
            outcomes.withIndex().associate {
                    (index, outcome) ->
                FeedId("f$index") to outcome
            },
            emptyMap(),
        )

    @Test
    fun summarisesEachShapeOfResult() {
        assertEquals("Nothing to refresh", FeedRefreshReport.EMPTY.summary())
        assertEquals("Already up to date", report(FeedRefreshOutcome.NotModified).summary())
        assertEquals("1 new article", report(FeedRefreshOutcome.Updated(1, 5)).summary())
        assertEquals(
            "5 new articles",
            report(FeedRefreshOutcome.Updated(2, 5), FeedRefreshOutcome.Updated(3, 5)).summary(),
        )
        assertEquals(
            "Refresh failed: Timed out",
            report(FeedRefreshOutcome.Failed(FeedRefreshFailure.TIMEOUT)).summary(),
        )
        assertEquals(
            "2 new, 1 of 2 feeds failed",
            report(FeedRefreshOutcome.Updated(2, 5), FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK)).summary(),
        )
    }

    @Test
    fun everyFailureHasALabel() {
        FeedRefreshFailure.entries.forEach { assert(it.label().isNotBlank()) }
    }
}

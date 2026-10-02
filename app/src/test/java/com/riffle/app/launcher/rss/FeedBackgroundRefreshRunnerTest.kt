package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.FeedBackgroundConditions
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunResult
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.RssSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedBackgroundRefreshRunnerTest {
    private val feed = FeedConfiguration(FeedId("a"), FeedUrl.parse("https://example.com/a.xml").getOrThrow())
    private var settings: RssSettings? =
        RssSettings(feeds = listOf(feed), refreshInterval = FeedRefreshIntervalOption.HOURS_6)
    private var conditions = FeedBackgroundConditions(meteredNetwork = false, batterySaver = false)
    private var report: FeedRefreshReport? = FeedRefreshReport.EMPTY
    private val requestedGaps = mutableListOf<Long>()
    private val records = mutableListOf<FeedBackgroundRunRecord>()
    private val runner =
        FeedBackgroundRefreshRunner(
            settings = { settings },
            conditions = { conditions },
            refreshScheduled = { gap ->
                requestedGaps += gap
                report
            },
            recordRun = { records += it },
            clock = { 42L },
        )

    private fun reportOf(vararg outcomes: FeedRefreshOutcome) =
        FeedRefreshReport(outcomes.withIndex().associate { (i, o) -> FeedId("f$i") to o }, emptyMap())

    @Test
    fun anUnreadableSettingsStoreDoesNothing() {
        settings = null

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))
        assertTrue(requestedGaps.isEmpty())
        assertTrue(records.isEmpty())
    }

    @Test
    fun staleWorkAfterTheSettingWentOffNeverTouchesTheNetworkOrRecordsAStatus() {
        settings = RssSettings(feeds = listOf(feed), refreshInterval = FeedRefreshIntervalOption.OFF)

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))
        assertTrue(requestedGaps.isEmpty())
        assertTrue(records.isEmpty())
    }

    @Test
    fun noFeedsMeansNoRefresh() {
        settings = RssSettings(feeds = emptyList(), refreshInterval = FeedRefreshIntervalOption.HOURS_6)

        runner.run(0)

        assertTrue(requestedGaps.isEmpty())
    }

    @Test
    fun aMeteredNetworkIsSkippedWhenWifiOnlyIsOnAndRecordedAsSkipped() {
        conditions = conditions.copy(meteredNetwork = true)

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))

        assertTrue(requestedGaps.isEmpty())
        assertEquals(listOf(FeedBackgroundRunRecord(42L, FeedBackgroundRunRecord.Kind.SKIPPED)), records)
    }

    @Test
    fun aMeteredNetworkIsAllowedWhenWifiOnlyIsOff() {
        conditions = conditions.copy(meteredNetwork = true)
        settings = settings!!.copy(backgroundWifiOnly = false)

        runner.run(0)

        assertEquals(1, requestedGaps.size)
    }

    @Test
    fun batterySaverIsSkipped() {
        conditions = conditions.copy(batterySaver = true)

        runner.run(0)

        assertTrue(requestedGaps.isEmpty())
        assertEquals(FeedBackgroundRunRecord.Kind.SKIPPED, records.single().kind)
    }

    @Test
    fun theSharedCoordinatorIsAskedWithHalfTheIntervalAsTheMinimumGap() {
        runner.run(0)

        assertEquals(listOf(3L * 60 * 60 * 1000), requestedGaps)
    }

    @Test
    fun aRunWhileAUserRefreshIsActiveDefersWithoutRecording() {
        report = null

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))
        assertTrue(records.isEmpty())
    }

    @Test
    fun updatedFeedsAreRecordedAndFinish() {
        report = reportOf(FeedRefreshOutcome.Updated(2, 5))

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))
        assertEquals(FeedBackgroundRunRecord(42L, FeedBackgroundRunRecord.Kind.UPDATED), records.single())
    }

    @Test
    fun allNetworkFailuresRetryUntilTheAttemptsAreUsedUp() {
        report = reportOf(FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK))

        assertEquals(FeedBackgroundRunResult.RETRY, runner.run(0))
        assertEquals(FeedBackgroundRunResult.RETRY, runner.run(2))
        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(3))
        assertEquals(FeedBackgroundRunRecord.Kind.FAILED, records.first().kind)
    }

    @Test
    fun aMalformedFeedDoesNotRetry() {
        report = reportOf(FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED))

        assertEquals(FeedBackgroundRunResult.SUCCESS, runner.run(0))
    }

    @Test
    fun nothingDueIsRecordedAsUnchanged() {
        report = FeedRefreshReport.EMPTY

        runner.run(0)

        assertEquals(FeedBackgroundRunRecord.Kind.UNCHANGED, records.single().kind)
        assertNull(records.firstOrNull { it.kind == FeedBackgroundRunRecord.Kind.FAILED })
    }
}

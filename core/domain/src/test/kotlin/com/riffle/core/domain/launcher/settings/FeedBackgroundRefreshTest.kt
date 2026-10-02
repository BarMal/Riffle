package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedBackgroundRefreshTest {
    private fun feed(
        id: String,
        enabled: Boolean = true,
    ) = FeedConfiguration(FeedId(id), FeedUrl.parse("https://example.com/$id.xml").getOrThrow(), enabled = enabled)

    private fun settings(
        interval: FeedRefreshIntervalOption = FeedRefreshIntervalOption.HOURS_3,
        feeds: List<FeedConfiguration> = listOf(feed("a")),
        wifiOnly: Boolean = true,
        chargingOnly: Boolean = false,
    ) = RssSettings(feeds, interval, wifiOnly, chargingOnly)

    private val idle = FeedBackgroundConditions(meteredNetwork = false, batterySaver = false)

    @Test
    fun defaultsAreOffWifiOnlyAndNotChargingOnly() {
        val defaults = RssSettings()

        assertEquals(FeedRefreshIntervalOption.OFF, defaults.refreshInterval)
        assertFalse(defaults.refreshInterval.isEnabled)
        assertTrue(defaults.backgroundWifiOnly)
        assertFalse(defaults.backgroundChargingOnly)
    }

    @Test
    fun intervalOptionsAreOffThenOneThreeSixTwelveTwentyFourHours() {
        assertEquals(
            listOf(0, 60, 180, 360, 720, 1440),
            FeedRefreshIntervalOption.entries.map { it.minutes },
        )
        assertEquals(FeedRefreshIntervalOption.OFF, FeedRefreshIntervalOption.HOURS_24.next())
    }

    @Test
    fun offCancelsEvenWithEnabledFeeds() {
        val decision = FeedBackgroundScheduler.decide(settings(interval = FeedRefreshIntervalOption.OFF))

        assertEquals(FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.INTERVAL_OFF), decision)
    }

    @Test
    fun noFeedsCancels() {
        val decision = FeedBackgroundScheduler.decide(settings(feeds = emptyList()))

        assertEquals(FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.NO_FEEDS), decision)
    }

    @Test
    fun onlyDisabledFeedsCancels() {
        val decision = FeedBackgroundScheduler.decide(settings(feeds = listOf(feed("a", enabled = false))))

        assertEquals(FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.NO_ENABLED_FEEDS), decision)
    }

    @Test
    fun anIntervalAndAnEnabledFeedSchedulesWithTheUsersConstraints() {
        val decision =
            FeedBackgroundScheduler.decide(
                settings(
                    interval = FeedRefreshIntervalOption.HOURS_6,
                    feeds = listOf(feed("a", enabled = false), feed("b")),
                    wifiOnly = false,
                    chargingOnly = true,
                ),
            )

        assertEquals(
            FeedBackgroundDecision.Schedule(
                FeedBackgroundSchedule(
                    intervalMinutes = 360,
                    requiresUnmeteredNetwork = false,
                    requiresCharging = true,
                ),
            ),
            decision,
        )
    }

    @Test
    fun everyNonOffIntervalSchedulesAtOrAboveWorkManagersFifteenMinuteFloor() {
        FeedRefreshIntervalOption.entries.filter { it.isEnabled }.forEach { option ->
            val decision = FeedBackgroundScheduler.decide(settings(interval = option))

            val schedule = (decision as FeedBackgroundDecision.Schedule).schedule
            assertTrue(schedule.intervalMinutes >= 15)
        }
    }

    @Test
    fun minimumFeedGapIsHalfTheInterval() {
        val schedule = FeedBackgroundSchedule(60, requiresUnmeteredNetwork = true, requiresCharging = false)

        assertEquals(30L * 60 * 1000, schedule.minFeedGapMillis)
    }

    @Test
    fun runGateRefusesWhenTheSettingWentOffAfterQueueing() {
        val gate = FeedBackgroundRunGate.check(settings(interval = FeedRefreshIntervalOption.OFF), idle)

        assertEquals(FeedBackgroundSkip.NOT_ENABLED, gate)
    }

    @Test
    fun runGateRefusesMeteredOnlyWhenWifiOnlyIsOn() {
        val metered = idle.copy(meteredNetwork = true)

        assertEquals(FeedBackgroundSkip.METERED_NETWORK, FeedBackgroundRunGate.check(settings(), metered))
        assertNull(FeedBackgroundRunGate.check(settings(wifiOnly = false), metered))
    }

    @Test
    fun runGateRefusesUnderBatterySaver() {
        val gate = FeedBackgroundRunGate.check(settings(wifiOnly = false), idle.copy(batterySaver = true))

        assertEquals(FeedBackgroundSkip.BATTERY_SAVER, gate)
    }

    @Test
    fun runGateAllowsAnIdleUnmeteredDevice() {
        assertNull(FeedBackgroundRunGate.check(settings(), idle))
    }

    private val updated = FeedRefreshOutcome.Updated(1, 1)
    private val network = FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK)
    private val malformed = FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED)

    @Test
    fun kindReflectsTheOutcomes() {
        assertEquals(FeedBackgroundRunRecord.Kind.UNCHANGED, FeedBackgroundOutcomes.kindOf(emptyList()))
        assertEquals(FeedBackgroundRunRecord.Kind.UPDATED, FeedBackgroundOutcomes.kindOf(listOf(network, updated)))
        assertEquals(FeedBackgroundRunRecord.Kind.FAILED, FeedBackgroundOutcomes.kindOf(listOf(network, malformed)))
        assertEquals(
            FeedBackgroundRunRecord.Kind.UNCHANGED,
            FeedBackgroundOutcomes.kindOf(listOf(FeedRefreshOutcome.NotModified, network)),
        )
    }

    @Test
    fun retriesOnlyWhenEveryFeedFailedTransientlyAndAttemptsRemain() {
        assertEquals(FeedBackgroundRunResult.RETRY, FeedBackgroundOutcomes.resultOf(listOf(network), 0))
        assertEquals(FeedBackgroundRunResult.SUCCESS, FeedBackgroundOutcomes.resultOf(listOf(network, updated), 0))
        assertEquals(FeedBackgroundRunResult.SUCCESS, FeedBackgroundOutcomes.resultOf(listOf(malformed), 0))
        assertEquals(FeedBackgroundRunResult.SUCCESS, FeedBackgroundOutcomes.resultOf(emptyList(), 0))
        assertEquals(
            FeedBackgroundRunResult.SUCCESS,
            FeedBackgroundOutcomes.resultOf(listOf(network), FeedBackgroundOutcomes.MAX_RETRY_ATTEMPTS),
        )
    }
}

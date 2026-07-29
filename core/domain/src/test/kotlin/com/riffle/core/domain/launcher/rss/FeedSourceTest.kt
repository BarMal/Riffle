package com.riffle.core.domain.launcher.rss

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedSourceTest {
    @Test
    fun scheduledRefreshIsSuppressedOnMeteredNetworkBeforeBatterySaverCheck() {
        assertEquals(
            FeedRefreshDecision.SUPPRESSED_METERED_NETWORK,
            DefaultFeedRefreshPolicy.decide(
                trigger = FeedRefreshTrigger.SCHEDULED,
                constraints = FeedRefreshConstraints(meteredNetwork = true, batterySaver = true),
            ),
        )
    }

    @Test
    fun scheduledRefreshIsSuppressedByBatterySaver() {
        assertEquals(
            FeedRefreshDecision.SUPPRESSED_BATTERY_SAVER,
            DefaultFeedRefreshPolicy.decide(
                trigger = FeedRefreshTrigger.SCHEDULED,
                constraints = FeedRefreshConstraints(meteredNetwork = false, batterySaver = true),
            ),
        )
    }

    @Test
    fun userRefreshIgnoresBackgroundNetworkConstraints() {
        assertEquals(
            FeedRefreshDecision.ALLOWED,
            DefaultFeedRefreshPolicy.decide(
                trigger = FeedRefreshTrigger.USER,
                constraints = FeedRefreshConstraints(meteredNetwork = true, batterySaver = true),
            ),
        )
    }
}

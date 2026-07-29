package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedRefreshConstraints
import com.riffle.core.domain.launcher.rss.FeedRefreshDecision
import com.riffle.core.domain.launcher.rss.FeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidFeedRefreshPolicyTest {
    @Test
    fun delegatesCurrentAndroidConstraintsToPolicy() {
        val delegate = RecordingPolicy()
        val policy =
            AndroidFeedRefreshPolicy(
                constraintsProvider = { FeedRefreshConstraints(meteredNetwork = true, batterySaver = true) },
                delegate = delegate,
            )

        assertEquals(
            FeedRefreshDecision.SUPPRESSED_METERED_NETWORK,
            policy.decide(FeedRefreshTrigger.SCHEDULED, FeedRefreshConstraints(false, false)),
        )
        assertEquals(FeedRefreshConstraints(meteredNetwork = true, batterySaver = true), delegate.constraints)
    }

    private class RecordingPolicy : FeedRefreshPolicy {
        var constraints: FeedRefreshConstraints? = null

        override fun decide(
            trigger: FeedRefreshTrigger,
            constraints: FeedRefreshConstraints,
        ): FeedRefreshDecision {
            this.constraints = constraints
            return if (constraints.meteredNetwork) {
                FeedRefreshDecision.SUPPRESSED_METERED_NETWORK
            } else {
                FeedRefreshDecision.ALLOWED
            }
        }
    }
}

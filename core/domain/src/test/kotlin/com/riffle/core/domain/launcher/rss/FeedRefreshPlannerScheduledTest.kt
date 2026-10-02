package com.riffle.core.domain.launcher.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Background (scheduled) planning, issue #1393: due feeds only, backoff obeyed, host safety kept. */
class FeedRefreshPlannerScheduledTest {
    private val planner =
        FeedRefreshPlanner(minIntervalMillis = 10_000, baseBackoffMillis = 60_000, maxBackoffMillis = 600_000)
    private val now = 10_000_000L
    private val hour = 60L * 60 * 1000

    private fun feed(
        id: String,
        url: String = "https://example.com/$id.xml",
        enabled: Boolean = true,
    ) = FeedConfiguration(FeedId(id), FeedUrl.parse(url).getOrThrow(), enabled = enabled)

    private fun scheduled(
        feeds: List<FeedConfiguration>,
        states: Map<FeedId, FeedRefreshState> = emptyMap(),
        scope: FeedRefreshScope = FeedRefreshScope.All,
        gap: Long = hour / 2,
    ) = planner.plan(
        feeds = feeds,
        scope = scope,
        states = states,
        profileStatuses = emptyMap(),
        nowEpochMillis = now,
        trigger = FeedRefreshTrigger.SCHEDULED,
        scheduledMinGapMillis = gap,
    )

    @Test
    fun scheduledRequestsCarryTheScheduledTrigger() {
        val plan = scheduled(listOf(feed("a")))

        assertTrue(plan.requests.all { it.trigger == FeedRefreshTrigger.SCHEDULED })
        assertEquals(1, plan.requests.size)
    }

    @Test
    fun aFeedAttemptedWithinTheScheduledGapIsLeftAlone() {
        val states = mapOf(FeedId("a") to FeedRefreshState(lastAttemptAtEpochMillis = now - hour / 4))

        val plan = scheduled(listOf(feed("a")), states)

        assertTrue(plan.requests.isEmpty())
        assertEquals(FeedRefreshSkip.TOO_SOON, plan.skipped[FeedId("a")])
    }

    @Test
    fun aFeedPastTheScheduledGapIsFetchedWithItsPersistedValidators() {
        val validators = FeedValidators(etag = "e1", lastModified = "Mon")
        val states =
            mapOf(
                FeedId("a") to
                    FeedRefreshState(lastAttemptAtEpochMillis = now - hour, validators = validators),
            )

        val plan = scheduled(listOf(feed("a")), states)

        assertEquals(validators, plan.requests.single().validators)
    }

    @Test
    fun backoffAppliesToScheduledRunsEvenWhenTheGapHasPassed() {
        val states =
            mapOf(
                FeedId("a") to
                    FeedRefreshState(lastAttemptAtEpochMillis = now - 20_000, consecutiveFailures = 4),
            )

        val plan = scheduled(listOf(feed("a")), states, gap = 0)

        assertEquals(FeedRefreshSkip.BACKING_OFF, plan.skipped[FeedId("a")])
    }

    @Test
    fun aScheduledPlanNeverUsesTheRefreshOnePathThatIgnoresBackoff() {
        val states =
            mapOf(
                FeedId("a") to
                    FeedRefreshState(lastAttemptAtEpochMillis = now - 20_000, consecutiveFailures = 4),
            )

        val plan = scheduled(listOf(feed("a")), states, scope = FeedRefreshScope.One(FeedId("a")), gap = 0)

        assertEquals(FeedRefreshSkip.BACKING_OFF, plan.skipped[FeedId("a")])
    }

    @Test
    fun scheduledRunsStillSkipDisabledAndUnsafeHostFeeds() {
        val plan =
            scheduled(
                listOf(
                    feed("off", enabled = false),
                    feed("local", url = "https://192.168.1.10/feed.xml"),
                    feed("ok"),
                ),
            )

        assertEquals(listOf(FeedId("ok")), plan.requests.map { it.configuration.id })
        assertEquals(FeedRefreshSkip.DISABLED, plan.skipped[FeedId("off")])
        assertEquals(FeedRefreshSkip.UNSAFE_URL, plan.skipped[FeedId("local")])
    }

    @Test
    fun theUserPathIsUnchangedByTheScheduledParameters() {
        val states = mapOf(FeedId("a") to FeedRefreshState(lastAttemptAtEpochMillis = now - 20_000))

        val plan =
            planner.plan(listOf(feed("a")), FeedRefreshScope.All, states, emptyMap(), now)

        assertEquals(FeedRefreshTrigger.USER, plan.requests.single().trigger)
    }
}

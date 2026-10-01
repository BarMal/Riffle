package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedRefreshPlannerTest {
    private val planner =
        FeedRefreshPlanner(minIntervalMillis = 10_000, baseBackoffMillis = 60_000, maxBackoffMillis = 600_000)
    private val now = 1_000_000L

    private fun feed(
        id: String,
        url: String = "https://example.com/$id.xml",
        enabled: Boolean = true,
        profile: AppProfile = AppProfile.personal(),
    ) = FeedConfiguration(FeedId(id), FeedUrl.parse(url).getOrThrow(), profile = profile, enabled = enabled)

    private fun plan(
        feeds: List<FeedConfiguration>,
        scope: FeedRefreshScope = FeedRefreshScope.All,
        states: Map<FeedId, FeedRefreshState> = emptyMap(),
        statuses: Map<com.riffle.core.domain.launcher.apps.AppProfileId, FeedProfileStatus> = emptyMap(),
    ) = planner.plan(feeds, scope, states, statuses, now)

    @Test
    fun refreshAllRequestsEveryEnabledFeedAsUserTriggered() {
        val result = plan(listOf(feed("a"), feed("b")))

        assertEquals(listOf(FeedId("a"), FeedId("b")), result.requests.map { it.configuration.id })
        assertTrue(result.requests.all { it.trigger == FeedRefreshTrigger.USER })
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun refreshOneOnlyTouchesThatFeedAndUnknownIdsPlanNothing() {
        val feeds = listOf(feed("a"), feed("b"))

        val one = plan(feeds, FeedRefreshScope.One(FeedId("b")))

        assertEquals(listOf(FeedId("b")), one.requests.map { it.configuration.id })
        assertTrue(plan(feeds, FeedRefreshScope.One(FeedId("zzz"))).requests.isEmpty())
    }

    @Test
    fun disabledAndLockedProfileFeedsAreExcluded() {
        val work = AppProfile.work()
        val result =
            plan(
                feeds = listOf(feed("off", enabled = false), feed("work", profile = work), feed("ok")),
                statuses = mapOf(work.id to FeedProfileStatus.LOCKED),
            )

        assertEquals(listOf(FeedId("ok")), result.requests.map { it.configuration.id })
        assertEquals(FeedRefreshSkip.DISABLED, result.skipped[FeedId("off")])
        assertEquals(FeedRefreshSkip.PROFILE_LOCKED, result.skipped[FeedId("work")])
    }

    @Test
    fun removedProfileFeedsAreExcluded() {
        val work = AppProfile.work()
        val result = plan(listOf(feed("work", profile = work)), statuses = mapOf(work.id to FeedProfileStatus.REMOVED))

        assertEquals(FeedRefreshSkip.PROFILE_REMOVED, result.skipped[FeedId("work")])
    }

    @Test
    fun privateHostsAreExcluded() {
        val result =
            plan(
                listOf(
                    feed("lan", url = "https://192.168.1.5/feed"),
                    feed("lo", url = "https://localhost/feed"),
                ),
            )

        assertTrue(result.requests.isEmpty())
        assertEquals(setOf(FeedRefreshSkip.UNSAFE_URL), result.skipped.values.toSet())
    }

    @Test
    fun conditionalValidatorsAreCarriedIntoTheRequest() {
        val validators = FeedValidators(etag = "\"v1\"", lastModified = "Mon, 01 Jan 2026 00:00:00 GMT")
        val state = FeedRefreshState(lastAttemptAtEpochMillis = now - 100_000, validators = validators)

        val request = plan(listOf(feed("a")), states = mapOf(FeedId("a") to state)).requests.single()

        assertEquals(validators, request.validators)
    }

    @Test
    fun repeatTapsWithinTheMinimumIntervalAreSkipped() {
        val state = FeedRefreshState(lastAttemptAtEpochMillis = now - 5_000)

        val all = plan(listOf(feed("a")), states = mapOf(FeedId("a") to state))
        val one = plan(listOf(feed("a")), FeedRefreshScope.One(FeedId("a")), mapOf(FeedId("a") to state))

        assertEquals(FeedRefreshSkip.TOO_SOON, all.skipped[FeedId("a")])
        assertEquals(FeedRefreshSkip.TOO_SOON, one.skipped[FeedId("a")])
    }

    @Test
    fun failedFeedsBackOffForRefreshAllButNotForExplicitRefreshOne() {
        val state = FeedRefreshState(lastAttemptAtEpochMillis = now - 30_000, consecutiveFailures = 1)
        val states = mapOf(FeedId("a") to state)

        assertEquals(FeedRefreshSkip.BACKING_OFF, plan(listOf(feed("a")), states = states).skipped[FeedId("a")])
        assertEquals(1, plan(listOf(feed("a")), FeedRefreshScope.One(FeedId("a")), states).requests.size)
    }

    @Test
    fun backoffDoublesAndIsCapped() {
        assertEquals(0, planner.backoffMillis(0))
        assertEquals(60_000, planner.backoffMillis(1))
        assertEquals(120_000, planner.backoffMillis(2))
        assertEquals(240_000, planner.backoffMillis(3))
        assertEquals(600_000, planner.backoffMillis(10))
        assertEquals(600_000, planner.backoffMillis(Int.MAX_VALUE))
    }

    @Test
    fun feedsRetryOnceTheBackoffHasElapsed() {
        val state = FeedRefreshState(lastAttemptAtEpochMillis = now - 61_000, consecutiveFailures = 1)

        assertEquals(1, plan(listOf(feed("a")), states = mapOf(FeedId("a") to state)).requests.size)
    }

    @Test
    fun aClockThatMovedBackwardsDoesNotFreezeTheFeed() {
        val state = FeedRefreshState(lastAttemptAtEpochMillis = now + 999_999, consecutiveFailures = 3)

        assertEquals(1, plan(listOf(feed("a")), states = mapOf(FeedId("a") to state)).requests.size)
    }

    @Test
    fun hostSafetyAcceptsPublicNamesAndRejectsLocalOnes() {
        assertTrue(FeedHostSafety.isPublicHost("example.com"))
        assertTrue(FeedHostSafety.isPublicHost("8.8.8.8"))
        listOf(
            "localhost", "printer.local", "intranet", "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255",
            "192.168.0.1", "169.254.1.1", "100.64.0.1", "0.0.0.0", "[::1]", "::1", "300.1.1.1", "2130706433", "",
        ).forEach { host -> assertFalse(FeedHostSafety.isPublicHost(host), host) }
        assertTrue(FeedHostSafety.isPublicHost("172.32.0.1"))
    }

    @Test
    fun sourceErrorsMapToTypedFailures() {
        assertEquals(FeedRefreshFailure.UNSAFE_URL, FeedSourceError.INVALID_REDIRECT.toRefreshFailure())
        assertEquals(FeedRefreshFailure.UNSAFE_URL, FeedSourceError.REDIRECT_LIMIT.toRefreshFailure())
        assertEquals(FeedRefreshFailure.TIMEOUT, FeedSourceError.TIMEOUT.toRefreshFailure())
        assertEquals(FeedRefreshFailure.OVERSIZE, FeedSourceError.RESPONSE_TOO_LARGE.toRefreshFailure())
        assertEquals(FeedRefreshFailure.MALFORMED, FeedSourceError.INVALID_ENCODING.toRefreshFailure())
        assertEquals(FeedRefreshFailure.NETWORK, FeedSourceError.NETWORK.toRefreshFailure())
        assertEquals(FeedRefreshFailure.BAD_STATUS, FeedSourceError.HTTP.toRefreshFailure())
    }

    @Test
    fun stateTracksFailuresAndResetsOnSuccessKeepingValidators() {
        val validators = FeedValidators(etag = "e")
        val timeout = FeedRefreshOutcome.Failed(FeedRefreshFailure.TIMEOUT)
        val failed = FeedRefreshState(validators = validators).after(timeout, 5, null)
        assertEquals(1, failed.consecutiveFailures)
        assertEquals(FeedRefreshFailure.TIMEOUT, failed.lastFailure)
        assertEquals(validators, failed.validators)

        val notModified = failed.after(FeedRefreshOutcome.NotModified, 9, FeedValidators())
        assertEquals(0, notModified.consecutiveFailures)
        assertEquals(null, notModified.lastFailure)
        assertEquals(9L, notModified.lastSuccessAtEpochMillis)
        assertEquals(validators, notModified.validators)

        val updated = notModified.after(FeedRefreshOutcome.Updated(2, 5), 12, FeedValidators(etag = "new"))
        assertEquals(FeedValidators(etag = "new"), updated.validators)
    }
}

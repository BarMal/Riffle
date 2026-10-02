package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.workspace.sources.ics.DefaultIcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.Executor

class IcsRefreshCoordinatorTest {
    private val id = IcsFeedId("feed-1")
    private val repository = loadedRepository()
    private val calls = mutableListOf<FeedFetchRequest>()
    private var now = START_MILLIS
    private var result: () -> FeedTransportResult = { FeedTransportResult.Content(CALENDAR, FeedValidators("e1")) }
    private val coordinator =
        IcsRefreshCoordinator(
            transport =
                FeedTransport { request ->
                    calls += request
                    result()
                },
            engine = DefaultIcsEngine(),
            repository = repository,
            executor = Executor { task -> task.run() },
            zone = { ZoneId.of("UTC") },
            clock = { now },
        )

    @Test
    fun nothingTouchesTheNetworkUnlessTheUserAsks() {
        coordinator.statusOf(id)
        coordinator.statusChanges.observe {}
        repository.cached(id)
        assertTrue(calls.isEmpty())
        assertFalse(coordinator.isRefreshing)
    }

    @Test
    fun successParsesPrunesAndStoresEventsNotTheBody() {
        val report = coordinator.refreshBlocking()!!

        val outcome = report.outcomes.getValue(id) as FeedRefreshOutcome.Updated
        assertEquals(2, outcome.totalArticles)
        val cached = repository.cached(id)!!
        // The 2020 single event is pruned; the weekly one and the 2026 one stay.
        assertEquals(listOf("weekly", "soon"), cached.events.map { it.uid })
        assertEquals(now, cached.fetchedAtEpochMillis)
        assertEquals(1, calls.size)
        assertTrue(calls.single().configuration.url.value.contains("SECRET-TOKEN-123"))
    }

    @Test
    fun aSecondRefreshSendsValidatorsAndANotModifiedKeepsTheCache() {
        coordinator.refreshBlocking()
        now += 60_000
        result = { FeedTransportResult.NotModified(FeedValidators("e1")) }

        val report = coordinator.refreshBlocking()!!

        assertEquals(FeedRefreshOutcome.NotModified, report.outcomes.getValue(id))
        assertEquals("e1", calls.last().validators?.etag)
        assertNotNull(repository.cached(id))
    }

    @Test
    fun repeatTapsAreSkippedAsTooSoon() {
        coordinator.refreshBlocking()
        val report = coordinator.refreshBlocking()!!
        assertEquals(FeedRefreshSkip.TOO_SOON, report.skipped.getValue(id))
        assertEquals(1, calls.size)
    }

    @Test
    fun failuresAreTypedAndKeepWhatWasCached() {
        coordinator.refreshBlocking()
        now += 60_000
        result = { FeedTransportResult.Failure(FeedSourceError.RESPONSE_TOO_LARGE) }

        val report = coordinator.refreshBlocking()!!

        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.OVERSIZE), report.outcomes.getValue(id))
        assertEquals(2, repository.cached(id)!!.events.size)
        assertEquals(FeedRefreshFailure.OVERSIZE, coordinator.statusOf(id).lastFailure)
    }

    @Test
    fun aBodyThatIsNotACalendarIsMalformedAndNothingIsStored() {
        result = { FeedTransportResult.Content("<html>secret-token</html>", FeedValidators()) }
        val report = coordinator.refreshBlocking()!!
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED), report.outcomes.getValue(id))
        assertNull(repository.cached(id))
    }

    @Test
    fun anEmptyParseDoesNotWipeExistingContent() {
        coordinator.refreshBlocking()
        now += 60_000
        result = { FeedTransportResult.Content("BEGIN:VCALENDAR\r\nEND:VCALENDAR", FeedValidators()) }
        val report = coordinator.refreshBlocking()!!
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED), report.outcomes.getValue(id))
        assertEquals(2, repository.cached(id)!!.events.size)
    }

    @Test
    fun anExceptionFromTheTransportBecomesANetworkFailureWithoutItsMessage() {
        result = { error("boom https://calendar.example.com/private/SECRET-TOKEN-123") }
        val report = coordinator.refreshBlocking()!!
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK), report.outcomes.getValue(id))
        assertFalse(report.toString().contains("SECRET"))
    }

    @Test
    fun disabledFeedsAreNotFetched() {
        repository.updateSettings { it.withEnabled(id, false) }
        val report = coordinator.refreshBlocking()!!
        assertEquals(FeedRefreshSkip.DISABLED, report.skipped.getValue(id))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun onlyOneRefreshRunsAtATime() {
        var queued: Runnable? = null
        val deferred =
            IcsRefreshCoordinator(
                transport = FeedTransport { FeedTransportResult.Failure(FeedSourceError.NETWORK) },
                engine = DefaultIcsEngine(),
                repository = repository,
                executor = Executor { task -> queued = task },
            )
        assertTrue(deferred.refresh())
        assertFalse(deferred.refresh())
        assertTrue(deferred.isRefreshing)
        queued!!.run()
        assertFalse(deferred.isRefreshing)
    }

    private companion object {
        /** 2026-03-10T12:00:00Z. */
        const val START_MILLIS = 1_773_144_000_000L

        val CALENDAR =
            listOf(
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT",
                "UID:old",
                "DTSTART:20200101T090000Z",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:soon",
                "DTSTART:20260311T090000Z",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:weekly",
                "DTSTART:20250101T090000Z",
                "RRULE:FREQ=WEEKLY",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n")
    }
}

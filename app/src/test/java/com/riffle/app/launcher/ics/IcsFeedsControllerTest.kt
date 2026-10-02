package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.workspace.sources.ics.DefaultIcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class IcsFeedsControllerTest {
    private val id = IcsFeedId("feed-1")
    private val repository = loadedRepository()
    private var fetches = 0
    private val coordinator =
        IcsRefreshCoordinator(
            transport =
                FeedTransport {
                    fetches++
                    FeedTransportResult.Failure(FeedSourceError.NETWORK)
                },
            engine = DefaultIcsEngine(),
            repository = repository,
            executor = Executor { task -> task.run() },
        )
    private val controller = IcsFeedsController(repository, coordinator)

    @Test
    fun openingAndObservingNeverFetches() {
        controller.open()
        controller.state.value
        controller.close()
        assertEquals(0, fetches)
    }

    @Test
    fun rowsShowTheHostAndNeverTheUrl() {
        controller.open()
        val row = controller.state.value.rows.single()
        assertEquals("calendar.example.com", row.host)
        assertEquals("Work", row.name)
        assertFalse(row.toString().contains("SECRET"))
    }

    @Test
    fun addValidatesAndReportsTheReason() {
        controller.open()
        assertEquals(IcsAddResult.ADDED, controller.add("Home", "https://home.example.com/a.ics"))
        assertEquals(2, controller.state.value.rows.size)
        assertEquals(IcsAddResult.INVALID_URL, controller.add("x", "http://example.com/a.ics"))
        assertEquals(IcsAddResult.INVALID_URL, controller.add("x", "nonsense"))
        assertEquals(IcsAddResult.NOT_PUBLIC_HOST, controller.add("x", "https://192.168.0.2/a.ics"))
        assertEquals(IcsAddResult.DUPLICATE_OR_FULL, controller.add("x", SECRET_URL))
        assertEquals(2, controller.state.value.rows.size)
    }

    @Test
    fun errorTextNeverEchoesTheInput() {
        IcsAddResult.entries.forEach { result ->
            assertFalse(IcsFeedsText.addResultLabel(result).orEmpty().contains("://"))
        }
        assertNull(IcsFeedsText.addResultLabel(IcsAddResult.ADDED))
    }

    @Test
    fun removingOrDisablingAFeedClearsItsCachedEvents() {
        repository.replaceCache(id, CachedIcsFeed(listOf(icsEvent("a")), 1L))
        controller.setEnabled(id, false)
        assertNull(repository.cached(id))
        assertFalse(repository.settings().feeds.single().enabled)

        repository.replaceCache(id, CachedIcsFeed(listOf(icsEvent("a")), 1L))
        controller.remove(id)
        assertNull(repository.cached(id))
        assertTrue(repository.settings().feeds.isEmpty())
    }

    @Test
    fun refreshRunsOnlyWhenAsked_andTheReportReachesTheState() {
        controller.open()
        assertEquals(0, fetches)
        assertTrue(controller.refreshAll())
        assertEquals(1, fetches)
        val report = controller.state.value.lastReport
        assertNotNull(report)
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK), report!!.outcomes.getValue(id))
        assertEquals("Refresh failed", IcsFeedsText.summary(report))
    }

    @Test
    fun stateReflectsLoadingBeforeTheStoreAnswers() {
        val state = IcsFeedsUiState()
        assertFalse(state.loaded)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun summariesAreShortAndPlain() {
        assertNull(IcsFeedsText.summary(null))
        assertEquals("No feeds to refresh", IcsFeedsText.summary(IcsRefreshReport.EMPTY))
        assertEquals(
            "Already up to date",
            IcsFeedsText.summary(IcsRefreshReport(emptyMap(), mapOf(id to FeedRefreshSkip.TOO_SOON))),
        )
        assertEquals(
            "Updated 1 feed",
            IcsFeedsText.summary(IcsRefreshReport(mapOf(id to FeedRefreshOutcome.Updated(1, 1)), emptyMap())),
        )
        assertEquals(
            "Updated 1 feed, 1 feed failed",
            IcsFeedsText.summary(
                IcsRefreshReport(
                    mapOf(
                        id to FeedRefreshOutcome.Updated(1, 1),
                        IcsFeedId("b") to FeedRefreshOutcome.Failed(FeedRefreshFailure.TIMEOUT),
                    ),
                    emptyMap(),
                ),
            ),
        )
        FeedRefreshFailure.entries.forEach { assertTrue(IcsFeedsText.failureLabel(it).isNotBlank()) }
    }
}

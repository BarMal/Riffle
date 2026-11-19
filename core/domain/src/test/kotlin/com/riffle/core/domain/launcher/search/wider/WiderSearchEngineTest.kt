package com.riffle.core.domain.launcher.search.wider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WiderSearchEngineTest {
    private class FakeSource(
        override val id: SearchSourceId,
        override val minQueryLength: Int = 1,
        override val debounceMillis: Long = 0,
        override val maxResults: Int = 12,
        private val latency: Long = 0,
        private val failWith: Exception? = null,
    ) : SearchSource {
        val queries = mutableListOf<String>()
        var finished = 0

        override suspend fun query(
            query: String,
            limit: Int,
        ): List<ScoredResult> {
            queries += query
            if (latency > 0) delay(latency)
            failWith?.let { throw it }
            finished++
            return listOf(ScoredResult(WiderSearchResult.SystemSetting("$id:$query", query, "a"), 80))
        }
    }

    @Test
    fun emitsAnEmptyPendingSnapshotFirstThenOneAsEachSourceFinishes() =
        runTest {
            val fast = FakeSource(SearchSourceId.SYSTEM_SETTINGS)
            val slow = FakeSource(SearchSourceId.CONTACTS, latency = 100)
            val snapshots =
                WiderSearchEngine(listOf(fast, slow))
                    .search("zo", setOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS), 5)
                    .toList()
            assertEquals(3, snapshots.size)
            assertEquals(setOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS), snapshots[0].pending)
            assertEquals(setOf(SearchSourceId.CONTACTS), snapshots[1].pending)
            assertTrue(snapshots[2].pending.isEmpty())
            assertEquals(setOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS), snapshots[2].results.keys)
        }

    @Test
    fun disabledSourcesAndTooShortQueriesNeverReachTheSource() =
        runTest {
            val off = FakeSource(SearchSourceId.FILES)
            val needsTwo = FakeSource(SearchSourceId.CONTACTS, minQueryLength = 2)
            val engine = WiderSearchEngine(listOf(off, needsTwo))
            engine.search("z", setOf(SearchSourceId.CONTACTS), 5).toList()
            engine.search("zo", setOf(SearchSourceId.CONTACTS), 5).toList()
            engine.search(" z ", setOf(SearchSourceId.CONTACTS), 5).toList()
            assertEquals(emptyList(), off.queries)
            assertEquals(listOf("zo"), needsTwo.queries)
        }

    @Test
    fun theDebounceHoldsTheSourceBackAndACancelledQueryNeverRuns() =
        runTest {
            val source = FakeSource(SearchSourceId.CONTACTS, debounceMillis = 150)
            val engine = WiderSearchEngine(listOf(source))
            val job = launch { engine.search("zo", setOf(SearchSourceId.CONTACTS), 5).collect { } }
            advanceTimeBy(100)
            assertEquals(emptyList(), source.queries)
            job.cancel()
            advanceUntilIdle()
            assertEquals(emptyList(), source.queries)
        }

    @Test
    fun aNewQueryCancelsTheSlowOldOneAndItsResultIsNeverShown() =
        runTest {
            val source = FakeSource(SearchSourceId.CONTACTS, latency = 500)
            val engine = WiderSearchEngine(listOf(source))
            val queries = MutableStateFlow("an")
            val shown = mutableListOf<WiderSearchSnapshot>()
            val job =
                launch {
                    queries.collectLatest { query ->
                        engine.search(query, setOf(SearchSourceId.CONTACTS), 5).collect { shown += it }
                    }
                }
            advanceTimeBy(100)
            queries.value = "ann"
            advanceUntilIdle()
            job.cancel()
            assertEquals(listOf("an", "ann"), source.queries)
            assertEquals(1, source.finished)
            val answered = shown.filter { it.results.isNotEmpty() }
            assertEquals(1, answered.size)
            assertEquals("ann", answered.single().query)
        }

    @Test
    fun aSourceThatFailsGivesNoResultsAndDoesNotStopTheOthers() =
        runTest {
            val broken = FakeSource(SearchSourceId.CONTACTS, failWith = IllegalStateException("provider gone"))
            val fine = FakeSource(SearchSourceId.SYSTEM_SETTINGS)
            val last =
                WiderSearchEngine(listOf(broken, fine))
                    .search("zo", setOf(SearchSourceId.CONTACTS, SearchSourceId.SYSTEM_SETTINGS), 5)
                    .toList()
                    .last()
            assertEquals(emptyList(), last.results.getValue(SearchSourceId.CONTACTS))
            assertEquals(1, last.results.getValue(SearchSourceId.SYSTEM_SETTINGS).size)
            assertTrue(last.pending.isEmpty())
        }

    @Test
    fun aSourceNeverReturnsMoreThanItsOwnCap() =
        runTest {
            val many =
                object : SearchSource {
                    override val id = SearchSourceId.SYSTEM_SETTINGS
                    override val maxResults = 2

                    override suspend fun query(
                        query: String,
                        limit: Int,
                    ) = (1..9).map { ScoredResult(WiderSearchResult.SystemSetting("s$it", "s$it", "a"), it) }
                }
            val last =
                WiderSearchEngine(listOf(many)).search("s", setOf(SearchSourceId.SYSTEM_SETTINGS), 5).toList().last()
            assertEquals(2, last.results.getValue(SearchSourceId.SYSTEM_SETTINGS).size)
            assertEquals(9, (last.results.getValue(SearchSourceId.SYSTEM_SETTINGS).first().score))
        }

    @Test
    fun anUnfinishedSourceDoesNotBlockTheOthersSnapshot() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val stuck =
                object : SearchSource {
                    override val id = SearchSourceId.FILES

                    override suspend fun query(
                        query: String,
                        limit: Int,
                    ): List<ScoredResult> {
                        gate.await()
                        return emptyList()
                    }
                }
            val fast = FakeSource(SearchSourceId.SYSTEM_SETTINGS)
            val seen = mutableListOf<WiderSearchSnapshot>()
            val job =
                launch {
                    WiderSearchEngine(listOf(stuck, fast))
                        .search("zo", setOf(SearchSourceId.FILES, SearchSourceId.SYSTEM_SETTINGS), 5)
                        .collect { seen += it }
                }
            advanceUntilIdle()
            assertEquals(setOf(SearchSourceId.FILES), seen.last().pending)
            assertEquals(setOf(SearchSourceId.SYSTEM_SETTINGS), seen.last().results.keys)
            job.cancel()
        }
}

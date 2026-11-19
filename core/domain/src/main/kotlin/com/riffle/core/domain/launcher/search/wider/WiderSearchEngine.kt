package com.riffle.core.domain.launcher.search.wider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Runs the enabled sources for one query. Apps are not here: they are answered instantly by the caller, and nothing
 * the sources do can delay them. Every source runs in its own coroutine after its own debounce; the flow emits a
 * snapshot as each finishes. Collecting the flow for a new query (`collectLatest`, or a cancelled `LaunchedEffect`)
 * cancels the running sources, so a stale answer is never emitted for a query that is no longer the current one.
 */
class WiderSearchEngine(
    private val sources: List<SearchSource>,
) {
    fun search(
        query: String,
        enabled: Set<SearchSourceId>,
        limit: Int,
    ): Flow<WiderSearchSnapshot> =
        flow {
            val trimmed = query.trim()
            val running =
                sources.filter { source -> source.id in enabled && trimmed.length >= source.minQueryLength }
            var snapshot = WiderSearchSnapshot(query = query, pending = running.map { it.id }.toSet())
            emit(snapshot)
            if (running.isEmpty()) return@flow
            val done = Channel<Pair<SearchSourceId, List<ScoredResult>>>(Channel.UNLIMITED)
            coroutineScope {
                running.forEach { source ->
                    launch {
                        if (source.debounceMillis > 0) delay(source.debounceMillis)
                        val found =
                            try {
                                source.query(trimmed, source.maxResults.coerceAtMost(limit.coerceAtLeast(1) * 2))
                                    .sortedByDescending { it.score }
                                    .take(source.maxResults)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (
                                @Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception,
                            ) {
                                // One failing source (a provider that went away) must not take the others down.
                                emptyList()
                            }
                        done.send(source.id to found)
                    }
                }
                repeat(running.size) {
                    val (id, found) = done.receive()
                    snapshot =
                        snapshot.copy(results = snapshot.results + (id to found), pending = snapshot.pending - id)
                    emit(snapshot)
                }
            }
        }
}

package com.riffle.core.domain.launcher.workspace.sources

/** Longest query the search source works with; longer input is cut. */
const val MAX_SEARCH_QUERY_LENGTH = 128

/**
 * The one transient place the current search text lives, so a lens over `SourceIds.SEARCH` can be driven
 * without a query input on `ItemSource` (see docs/product/workspaces-sources-rss-search.md).
 *
 * A search-box UI or container calls [set]; the search source observes it and re-queries. All lenses over
 * the search source share this one query, matching the one-subscription-per-source rule.
 *
 * The text is memory-only by design: nothing here serializes it, [toString] never reveals it, and it is
 * never passed to a logger, a diagnostic or a backup. Thread-safe; listeners run outside the lock.
 */
class SearchQueryHolder {
    private val lock = Any()
    private var query = ""
    private val listeners = LinkedHashSet<Listener>()

    /** The current normalized query, empty when nothing is being searched. */
    fun current(): String = synchronized(lock) { query }

    /** Trims and bounds [raw]; listeners are notified only when the normalized query actually changed. */
    fun set(raw: String) {
        val next = raw.trim().take(MAX_SEARCH_QUERY_LENGTH).trim()
        val targets =
            synchronized(lock) {
                if (next == query) return
                query = next
                listeners.toList()
            }
        targets.forEach { listener -> listener.onChanged() }
    }

    fun clear() = set("")

    /** Calls [onChanged] after every change until the returned function is invoked. May fire from any thread. */
    fun observe(onChanged: () -> Unit): () -> Unit {
        val listener = Listener(onChanged)
        synchronized(lock) { listeners += listener }
        return { synchronized(lock) { listeners -= listener } }
    }

    override fun toString(): String = "SearchQueryHolder"

    private class Listener(val onChanged: () -> Unit)
}

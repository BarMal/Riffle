package com.riffle.core.domain.launcher.search.wider

/** One source's results as drawn: at most the cap's worth, with how many "Show more" would add. */
data class SearchGroup(
    val source: SearchSourceId,
    val rows: List<WiderSearchResult>,
    val hiddenCount: Int = 0,
    /** The source is on but lacks its permission: the group is one inline "Allow" row. */
    val needsPermission: Boolean = false,
)

/**
 * The merged result list: calculator [answers] first, then the apps ([primary], the instant results, untouched), then
 * the [groups] in the person's order.
 */
data class MergedSearch<P>(
    val answers: List<WiderSearchResult>,
    val primary: List<P>,
    val groups: List<SearchGroup>,
) {
    /** More than one group has something to show: only then do headers earn their room. */
    val showHeaders: Boolean
        get() = (if (answers.isNotEmpty()) 1 else 0) + (if (primary.isNotEmpty()) 1 else 0) + groups.size > 1

    /** The result Enter acts on when it is not an app: the answer, else the first row of the first group. */
    val topWiderResult: WiderSearchResult?
        get() =
            answers.firstOrNull()
                ?: groups.takeIf { primary.isEmpty() }?.firstNotNullOfOrNull { group -> group.rows.firstOrNull() }
}

/** What the sources returned so far for one query. */
data class WiderSearchSnapshot(
    val query: String,
    val results: Map<SearchSourceId, List<ScoredResult>> = emptyMap(),
    /** Sources still running for [query]. */
    val pending: Set<SearchSourceId> = emptySet(),
)

object WiderSearchMerger {
    const val MIN_PER_GROUP = 3

    fun <P> merge(
        primary: List<P>,
        snapshot: WiderSearchSnapshot,
        order: List<SearchSourceId>,
        perGroup: Int,
        expanded: Set<SearchSourceId> = emptySet(),
        needsPermission: Set<SearchSourceId> = emptySet(),
    ): MergedSearch<P> {
        val cap = perGroup.coerceAtLeast(1)
        val answers =
            snapshot.results[SearchSourceId.CALCULATOR].orEmpty()
                .sortedByDescending { it.score }
                .map { it.result }
                .take(1)
        val groups =
            completeOrder(order).mapNotNull { source ->
                when {
                    source in needsPermission -> SearchGroup(source, emptyList(), needsPermission = true)
                    else -> group(source, snapshot.results[source].orEmpty(), cap, source in expanded)
                }
            }
        return MergedSearch(answers = answers, primary = primary, groups = groups)
    }

    /** The saved order, without repeats or the calculator, with any group it left out added in the default order. */
    fun completeOrder(order: List<SearchSourceId>): List<SearchSourceId> =
        (order.filter { it.isGroup } + SearchSourceId.DEFAULT_GROUP_ORDER).distinct()

    private fun group(
        source: SearchSourceId,
        scored: List<ScoredResult>,
        cap: Int,
        expanded: Boolean,
    ): SearchGroup? {
        val (trailing, hits) = scored.sortedByDescending { it.score }.map { it.result }.partition { it.trailing }
        val shown = if (expanded) hits else hits.take(cap)
        return if (hits.isEmpty() && trailing.isEmpty()) {
            null
        } else {
            SearchGroup(source, shown + trailing, hiddenCount = hits.size - shown.size)
        }
    }
}

package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome

/** Short, URL-free, content-free wording for a failed refresh. */
fun FeedRefreshFailure.label(): String =
    when (this) {
        FeedRefreshFailure.NETWORK -> "Network error"
        FeedRefreshFailure.TIMEOUT -> "Timed out"
        FeedRefreshFailure.BAD_STATUS -> "Server error"
        FeedRefreshFailure.OVERSIZE -> "Feed too large"
        FeedRefreshFailure.MALFORMED -> "Not a valid feed"
        FeedRefreshFailure.UNSAFE_URL -> "Unsafe address"
    }

/** One-line result of a refresh for the settings row; never names feeds, URLs or articles. */
fun FeedRefreshReport.summary(): String {
    val outcomeList = outcomes.values
    val newArticles = outcomeList.filterIsInstance<FeedRefreshOutcome.Updated>().sumOf { it.newArticles }
    val failures = outcomeList.filterIsInstance<FeedRefreshOutcome.Failed>()
    return when {
        outcomeList.isEmpty() -> "Nothing to refresh"
        failures.isEmpty() && newArticles == 0 -> "Already up to date"
        failures.isEmpty() -> "$newArticles new ${if (newArticles == 1) "article" else "articles"}"
        failures.size == outcomeList.size -> "Refresh failed: ${failures.first().reason.label()}"
        else -> "$newArticles new, ${failures.size} of ${outcomeList.size} feeds failed"
    }
}

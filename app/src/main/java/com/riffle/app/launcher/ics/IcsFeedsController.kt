package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedUrlProblem
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One feed as a settings row shows it: the host only, never the URL. */
internal data class IcsFeedRow(
    val id: IcsFeedId,
    val name: String,
    val host: String,
    val enabled: Boolean,
    val refreshing: Boolean,
    val lastUpdatedAtEpochMillis: Long?,
    val lastFailure: FeedRefreshFailure?,
)

internal data class IcsFeedsUiState(
    val loaded: Boolean = false,
    val rows: List<IcsFeedRow> = emptyList(),
    val refreshing: Boolean = false,
    val lastReport: IcsRefreshReport? = null,
)

internal enum class IcsAddResult {
    ADDED,
    INVALID_URL,
    NOT_PUBLIC_HOST,
    DUPLICATE_OR_FULL,
    NOT_READY,
}

/**
 * State holder for Settings > Calendar feeds (ICS) and the Sources page's feed section. No Android types.
 * While open ([open] to [close]) it follows the repository and the refresh status; nothing here fetches except
 * [refreshAll], which is only called from a tap. Removing or disabling a feed clears its cached events.
 */
internal class IcsFeedsController(
    private val repository: CachedIcsFeedRepository,
    private val coordinator: IcsRefreshCoordinator,
) {
    private val mutableState = MutableStateFlow(IcsFeedsUiState())
    private var stops: List<() -> Unit> = emptyList()

    @Volatile
    private var lastReport: IcsRefreshReport? = null

    val state: StateFlow<IcsFeedsUiState> = mutableState.asStateFlow()

    /** Starts following changes. Idempotent. */
    fun open() {
        if (stops.isNotEmpty()) return
        stops = listOf(repository.observe(::publish), coordinator.statusChanges.observe(::publish))
        publish()
    }

    /** Stops following changes. Idempotent. */
    fun close() {
        stops.forEach { it() }
        stops = emptyList()
    }

    fun add(
        name: String,
        url: String,
    ): IcsAddResult {
        if (!repository.isLoaded) return IcsAddResult.NOT_READY
        val problem = IcsFeedUrls.problemWith(url)
        val added = if (problem == null) repository.updateSettings { it.withAdded(name, url) } else null
        return when {
            problem == IcsFeedUrlProblem.NOT_A_VALID_HTTPS_URL -> IcsAddResult.INVALID_URL
            problem == IcsFeedUrlProblem.NOT_A_PUBLIC_HOST -> IcsAddResult.NOT_PUBLIC_HOST
            added == null -> IcsAddResult.DUPLICATE_OR_FULL
            else -> IcsAddResult.ADDED
        }
    }

    fun remove(id: IcsFeedId) {
        repository.updateSettings { it.withoutFeed(id) }
        repository.clearCache(id)
    }

    fun setEnabled(
        id: IcsFeedId,
        enabled: Boolean,
    ) {
        repository.updateSettings { it.withEnabled(id, enabled) }
        if (!enabled) repository.clearCache(id)
    }

    /** User-triggered: fetch every enabled feed on the refresh thread. False when one is already running. */
    fun refreshAll(): Boolean =
        coordinator.refresh(IcsRefreshScope.All) { report ->
            lastReport = report
            publish()
        }

    private fun publish() {
        mutableState.value =
            IcsFeedsUiState(
                loaded = repository.isLoaded,
                rows = repository.settings().feeds.map(::row),
                refreshing = coordinator.isRefreshing,
                lastReport = lastReport,
            )
    }

    private fun row(feed: IcsFeed): IcsFeedRow {
        val status = coordinator.statusOf(feed.id)
        return IcsFeedRow(
            id = feed.id,
            name = feed.name,
            host = feed.host,
            enabled = feed.enabled,
            refreshing = status.refreshing,
            lastUpdatedAtEpochMillis = status.lastUpdatedAtEpochMillis,
            lastFailure = status.lastFailure,
        )
    }
}

/** Plain-language text for the feed pages; never contains a URL or calendar content. */
internal object IcsFeedsText {
    const val TITLE = "Calendar feeds (ICS)"
    const val INTRO =
        "Read-only calendar feeds from https .ics links. Riffle only fetches when you tap Refresh. " +
            "A link can contain a secret, so only the host is shown and it is never backed up."

    fun countLabel(count: Int): String =
        when (count) {
            0 -> "No feeds yet. Add one to see its events."
            1 -> "1 feed"
            else -> "$count feeds"
        }

    fun failureLabel(failure: FeedRefreshFailure): String =
        when (failure) {
            FeedRefreshFailure.NETWORK -> "Network error"
            FeedRefreshFailure.TIMEOUT -> "Timed out"
            FeedRefreshFailure.BAD_STATUS -> "The server refused the request"
            FeedRefreshFailure.OVERSIZE -> "Feed is too large"
            FeedRefreshFailure.MALFORMED -> "Not a readable calendar"
            FeedRefreshFailure.UNSAFE_URL -> "Link not allowed"
        }

    fun addResultLabel(result: IcsAddResult): String? =
        when (result) {
            IcsAddResult.ADDED -> null
            IcsAddResult.INVALID_URL -> "Enter an https link to an .ics calendar"
            IcsAddResult.NOT_PUBLIC_HOST -> "That address is not a public host"
            IcsAddResult.DUPLICATE_OR_FULL -> "Already added, or the list is full (${IcsFeedSettings.MAX_ICS_FEEDS})"
            IcsAddResult.NOT_READY -> "Still loading, try again"
        }

    /** A one-line result of the last refresh, or null when nothing ran. */
    fun summary(report: IcsRefreshReport?): String? {
        if (report == null) return null
        val outcomes = report.outcomes.values
        val failed = outcomes.count { it is FeedRefreshOutcome.Failed }
        val updated = outcomes.count { it is FeedRefreshOutcome.Updated }
        val unchanged = outcomes.count { it is FeedRefreshOutcome.NotModified }
        return when {
            outcomes.isEmpty() && report.skipped.values.any { it == FeedRefreshSkip.TOO_SOON } -> "Already up to date"
            outcomes.isEmpty() -> "No feeds to refresh"
            failed == 0 && updated > 0 -> "Updated ${feedCount(updated)}"
            failed == 0 && unchanged > 0 -> "Already up to date"
            failed == outcomes.size -> "Refresh failed"
            else -> "Updated ${feedCount(updated)}, ${feedCount(failed)} failed"
        }
    }

    private fun feedCount(count: Int): String = if (count == 1) "1 feed" else "$count feeds"
}

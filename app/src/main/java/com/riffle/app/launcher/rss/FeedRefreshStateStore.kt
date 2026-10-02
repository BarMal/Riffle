package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshState
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord

/**
 * Persisted refresh bookkeeping that must outlive the process (issue #1393): per-feed validators, failure
 * counters and timestamps, and the last background run. Device-local and never backed up; it holds no URLs and
 * no article content. Defaults keep fakes that predate it compiling.
 */
interface FeedRefreshStateStore {
    fun loadRefreshStates(): Map<FeedId, FeedRefreshState> = emptyMap()

    /** Merges [states] into the persisted bookkeeping, bounded to the configurable feed count. */
    fun saveRefreshStates(states: Map<FeedId, FeedRefreshState>) = Unit

    /** The last background refresh run, if any ran. */
    fun loadBackgroundRun(): FeedBackgroundRunRecord? = null

    fun saveBackgroundRun(record: FeedBackgroundRunRecord) = Unit
}

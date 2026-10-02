package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream
import com.riffle.core.domain.launcher.workspace.sources.ics.DefaultIcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedEvents
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsSourceReader
import java.time.ZoneId

/**
 * What the ICS calendar-feed source reads: the configured feeds and the parsed events cached by the
 * user-triggered refresh. There is no network here by design; [changes] (a refresh stored events, or the feed
 * list changed) is what makes the source `LIVE`. [feeds] is null while the stores are still loading, which
 * the source reports as `Loading`.
 */
internal class IcsSourceDependencies(
    val feeds: () -> List<IcsFeed>?,
    val cachedEvents: (IcsFeedId) -> List<IcsEvent>?,
    val engine: IcsEngine = DefaultIcsEngine(),
    val zone: () -> ZoneId = ZoneId::systemDefault,
    val windowDays: Long = IcsSourceReader.DEFAULT_WINDOW_DAYS,
    val changes: SourceChangeSource = SourceChangeSource.NONE,
) {
    companion object {
        /** No feeds: the source reports an empty list and nothing else happens. */
        val NONE = IcsSourceDependencies(feeds = { emptyList() }, cachedEvents = { null })
    }
}

/**
 * Calendar feeds, read from the offline cache. Like the device calendar it is privacy sensitive (events the
 * feed marks private or confidential are `SENSITIVE`), and it is groupable by feed. Disabled feeds show nothing.
 */
internal fun icsSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(
        deps,
        SourceIds.ICS,
        setOf(SourceCapability.PRIVACY_SENSITIVE, SourceCapability.GROUPABLE),
        deps.ics.changes,
    ) {
        val ics = deps.ics
        val feeds = ics.feeds()
        if (feeds == null) {
            SourceState.Loading
        } else {
            val withEvents =
                feeds.filter(IcsFeed::enabled).mapNotNull { feed ->
                    ics.cachedEvents(feed.id)?.let { events -> IcsFeedEvents(feed.id, feed.name, events) }
                }
            SourceState.Ready(
                IcsSourceReader(ics.engine).items(withEvents, deps.nowEpochMillis(), ics.zone(), ics.windowDays),
            )
        }
    }

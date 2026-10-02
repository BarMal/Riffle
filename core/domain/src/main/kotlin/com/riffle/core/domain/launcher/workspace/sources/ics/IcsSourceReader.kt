package com.riffle.core.domain.launcher.workspace.sources.ics

import com.riffle.core.domain.launcher.workspace.Item
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The cached events of one enabled feed. */
data class IcsFeedEvents(
    val feedId: IcsFeedId,
    val feedName: String,
    val events: List<IcsEvent>,
)

/**
 * The read side of the ICS source: expands the cached events of every feed over the next [windowDays] and maps
 * what is running or upcoming to items, soonest first, at most [limit]. Pure: no I/O, no network.
 */
class IcsSourceReader(
    private val engine: IcsEngine,
    private val mapper: IcsItemMapper = IcsItemMapper(),
) {
    fun items(
        feeds: List<IcsFeedEvents>,
        nowEpochMillis: Long,
        zone: ZoneId,
        windowDays: Long = DEFAULT_WINDOW_DAYS,
        limit: Int = IcsItemMapper.DEFAULT_LIMIT,
    ): List<Item> {
        val from = Instant.ofEpochMilli(nowEpochMillis)
        val expansion =
            IcsExpansion(
                window = TimeWindow(from, from.plus(Duration.ofDays(windowDays.coerceIn(1L, MAX_WINDOW_DAYS)))),
                deviceZone = zone,
                maxOccurrences = limit.coerceAtLeast(1),
            )
        val expanded =
            feeds.map { feed ->
                IcsFeedOccurrences(feed.feedId, feed.feedName, engine.expand(feed.events, expansion))
            }
        return mapper.items(expanded, nowEpochMillis, limit)
    }

    companion object {
        const val DEFAULT_WINDOW_DAYS = 14L
        const val MAX_WINDOW_DAYS = 60L
    }
}

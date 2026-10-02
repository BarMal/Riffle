package com.riffle.core.domain.launcher.workspace.sources.ics

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/** The expanded instances of one feed, with the name its items are grouped under. */
data class IcsFeedOccurrences(
    val feedId: IcsFeedId,
    val feedName: String,
    val occurrences: List<IcsOccurrence>,
)

/**
 * Maps expanded feed instances to [Item]s the way `CalendarItemMapper` maps device events: the same `calendar.*`
 * extras, and events the feed marks private or confidential are [ItemPrivacy.SENSITIVE] (text is stripped at
 * projection). Items have no tap target: a read-only feed has nothing to open.
 */
class IcsItemMapper {
    /** The next [limit] instances that have not ended at [nowEpochMillis]: ongoing first, then by start. */
    fun items(
        feeds: List<IcsFeedOccurrences>,
        nowEpochMillis: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<Item> =
        feeds
            .flatMap { feed -> feed.occurrences.map { feed to it } }
            .filter { (_, o) -> o.endEpochMillis > nowEpochMillis || o.startEpochMillis >= nowEpochMillis }
            .sortedWith(compareBy({ it.second.startEpochMillis }, { it.first.feedId.value }, { it.second.uid }))
            .take(limit.coerceAtLeast(0))
            .map { (feed, occurrence) -> occurrence.toItem(feed) }

    private fun IcsOccurrence.toItem(feed: IcsFeedOccurrences): Item =
        Item(
            id = ItemId("${SourceIds.ICS.value}:${feed.feedId.value}:$uid:$startEpochMillis"),
            sourceId = SourceIds.ICS,
            target = ItemTarget.None,
            title = title.takeIf(String::isNotBlank),
            subtitle = location?.takeIf(String::isNotBlank) ?: feed.feedName,
            timeEpochMillis = startEpochMillis,
            groupKey = feed.feedId.value,
            groupLabel = feed.feedName,
            privacy = if (isPrivate) ItemPrivacy.SENSITIVE else ItemPrivacy.VISIBLE,
            ext =
                mapOf(
                    END_KEY to ItemExtValue.Number(endEpochMillis),
                    ALL_DAY_KEY to ItemExtValue.Flag(allDay),
                    RECURRING_KEY to ItemExtValue.Flag(recurring),
                ),
        )

    companion object {
        const val DEFAULT_LIMIT = 50
        val END_KEY = ItemExtKey("calendar.end")
        val ALL_DAY_KEY = ItemExtKey("calendar.all_day")
        val RECURRING_KEY = ItemExtKey("ics.recurring")
    }
}

package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

internal const val NOW = 864_000_000L + 12 * 3_600_000L
internal const val HOUR = 3_600_000L

internal fun notificationItem(
    key: String,
    pkg: String = "chat",
    app: String = "Chat",
    posted: Long = NOW - 10,
    priority: Long = 3,
    privacy: ItemPrivacy = ItemPrivacy.VISIBLE,
    source: SourceId = SourceIds.NOTIFICATIONS,
) = Item(
    id = ItemId("${source.value}:personal:$key"),
    sourceId = source,
    target = ItemTarget.App(pkg, "personal"),
    title = "Title $key",
    subtitle = if (source == SourceIds.MEDIA) "Artist $key" else null,
    body = if (source == SourceIds.MEDIA) null else "Text $key",
    icon = ItemImageHandle("icon:$pkg"),
    image = ItemImageHandle("art:$key"),
    timeEpochMillis = posted,
    groupKey = "$pkg:personal",
    groupLabel = app,
    actions = listOf(ItemAction.Open(), ItemAction.Dismiss()),
    privacy = privacy,
    ext =
        mapOf(
            ItemExtKey("notification.category") to ItemExtValue.Text("message"),
            ItemExtKey("notification.priority") to ItemExtValue.Number(priority),
        ),
)

internal fun eventItem(
    id: String,
    start: Long,
    end: Long = start + HOUR,
    source: SourceId = SourceIds.CALENDAR,
    privacy: ItemPrivacy = ItemPrivacy.VISIBLE,
    feed: String? = null,
) = Item(
    id = ItemId("${source.value}:$id"),
    sourceId = source,
    target = ItemTarget.DeepLink("content://events/$id"),
    title = "Event $id",
    subtitle = "Room $id",
    timeEpochMillis = start,
    groupKey = feed,
    groupLabel = feed,
    actions = listOf(ItemAction.Open()),
    privacy = privacy,
    ext =
        mapOf(
            ItemExtKey("calendar.end") to ItemExtValue.Number(end),
            ItemExtKey("calendar.all_day") to ItemExtValue.Flag(false),
        ),
)

internal fun articleItem(
    id: String,
    feed: String,
    published: Long?,
) = Item(
    id = ItemId("rss:$feed:$id"),
    sourceId = SourceIds.RSS,
    target = ItemTarget.DeepLink("https://example.org/$id"),
    title = "Article $id",
    subtitle = feed,
    body = "Summary $id",
    image = ItemImageHandle("feed:$id"),
    timeEpochMillis = published,
    groupKey = feed,
    groupLabel = feed,
    actions = listOf(ItemAction.Open()),
    ext =
        mapOf(
            ItemExtKey("rss.author") to ItemExtValue.Text("Ann"),
            ItemExtKey("rss.stale") to ItemExtValue.Flag(false),
        ),
)

internal fun recentItem(
    pkg: String,
    usedAt: Long,
    profile: String = "personal",
) = Item(
    id = ItemId("apps.recent:$profile:$pkg/Main"),
    sourceId = SourceIds.RECENT_APPS,
    target = ItemTarget.App(pkg, profile),
    title = pkg.uppercase(),
    icon = ItemImageHandle("icon:$pkg"),
    timeEpochMillis = usedAt,
    actions = listOf(ItemAction.Open()),
)

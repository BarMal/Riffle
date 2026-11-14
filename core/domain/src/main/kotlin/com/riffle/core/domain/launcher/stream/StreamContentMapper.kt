package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.LensProjection
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper

/**
 * Maps the `Item`s the sources already produce to [StreamContent]. Redaction is the lens pipeline's own
 * ([LensProjection.redact] with every field projected): the source's content level ceiling and the sensitive-field
 * rule apply first, then the typed value is built from what is left. A content type is never built from
 * unredacted text, so nothing here can widen what a lens would show.
 *
 * Items of a source this mapper does not know, or whose target cannot belong to the type (an app item without an
 * app target), map to null.
 */
object StreamContentMapper {
    private val END = ItemExtKey("calendar.end")
    private val ALL_DAY = ItemExtKey("calendar.all_day")
    private val RECURRING = ItemExtKey("ics.recurring")
    private val CATEGORY = ItemExtKey("notification.category")
    private val PRIORITY = ItemExtKey("notification.priority")
    private val AUTHOR = ItemExtKey("rss.author")
    private val STALE = ItemExtKey("rss.stale")
    private val NOTIFICATION_KEY = NotificationItemMapper.KEY_KEY

    /** Set by the platform RSS source: the opaque digest the cache keys read state by, and whether it was read. */
    val ARTICLE_DIGEST = ItemExtKey("rss.digest")
    val ARTICLE_READ = ItemExtKey("rss.read")

    fun map(
        item: Item,
        levels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
    ): StreamContent? {
        val redacted = LensProjection.redact(item, ItemField.ALL, levels)
        val hidden = levels.levelOf(item.sourceId) == SourceContentLevel.HIDE_TEXT
        val privacy =
            if (item.privacy == ItemPrivacy.SENSITIVE || hidden) StreamPrivacy.REDACTED else StreamPrivacy.VISIBLE
        // Message history and the queue are body text: they follow the BODY field, so "hide body" and sensitive
        // items drop them even though the ext map itself survives the hide-body level.
        val visible = LensProjection.visibleFields(item, ItemField.ALL, levels)
        val showBody = ItemField.BODY in visible
        // The contact photo and a message's picture are images: they follow the IMAGE field, as the large icon does.
        val showImages = ItemField.IMAGE in visible
        return when (item.sourceId) {
            SourceIds.NOTIFICATIONS -> notification(redacted, privacy, showBody, showImages)
            SourceIds.MEDIA -> media(redacted, privacy, showBody)
            SourceIds.CALENDAR -> event(redacted, privacy, EventKind.CALENDAR)
            SourceIds.ICS -> event(redacted, privacy, EventKind.CALENDAR_FEED)
            SourceIds.RSS -> article(redacted, privacy)
            SourceIds.RECENT_APPS -> recentApp(redacted)
            else -> null
        }
    }

    /** A task through the [StreamSourceIds.TASKS] content level. */
    fun redact(
        task: TaskContent,
        levels: SourceContentLevels,
    ): TaskContent =
        when (levels.levelOf(StreamSourceIds.TASKS)) {
            SourceContentLevel.SHOW -> task
            SourceContentLevel.HIDE_BODY -> task
            SourceContentLevel.HIDE_TEXT ->
                task.copy(title = null, listName = null, privacy = StreamPrivacy.REDACTED, actions = emptySet())
        }

    /** The next alarm as an event; its label is text, so it follows the [StreamSourceIds.ALARM] content level. */
    fun alarm(
        alarm: NextAlarm,
        levels: SourceContentLevels,
    ): EventContent {
        val hidden = levels.levelOf(StreamSourceIds.ALARM) == SourceContentLevel.HIDE_TEXT
        return EventContent(
            id = StreamContentId("${StreamSourceIds.ALARM.value}:${alarm.id}"),
            kind = EventKind.ALARM,
            title = alarm.label?.takeIf { it.isNotBlank() && !hidden },
            location = null,
            calendarName = null,
            startEpochMillis = alarm.triggerEpochMillis,
            endEpochMillis = null,
            allDay = false,
            recurring = false,
            target = ItemTarget.Intent(OPEN_CLOCK_TOKEN),
            actions = setOf(EventAction.OPEN),
            privacy = if (hidden) StreamPrivacy.REDACTED else StreamPrivacy.VISIBLE,
        )
    }

    /** The token an alarm's [ItemTarget.Intent] carries; the platform resolves it to the clock app. */
    const val OPEN_CLOCK_TOKEN = "alarm.open_clock"

    private fun notification(
        item: Item,
        privacy: StreamPrivacy,
        showBody: Boolean,
        showImages: Boolean,
    ): NotificationContent? {
        val app = item.appRef() ?: return null
        val category = (item.ext[CATEGORY] as? ItemExtValue.Text)?.value
        val rank = (item.ext[PRIORITY] as? ItemExtValue.Number)?.value
        return NotificationContent(
            id = StreamContentId(item.id.value),
            app = app,
            appLabel = item.groupLabel,
            title = item.title,
            text = item.body,
            icon = item.icon,
            image = item.image,
            postedAtEpochMillis = item.timeEpochMillis ?: 0L,
            category =
                category?.let { name -> NotificationCategory.entries.firstOrNull { it.name.lowercase() == name } },
            priority = rank?.let { value -> NotificationPriority.entries.firstOrNull { it.rank.toLong() == value } },
            target = item.target,
            actions = StreamContentDetails.notificationActions(item),
            privacy = privacy,
            notificationKey = (item.ext[NOTIFICATION_KEY] as? ItemExtValue.Text)?.value,
            thread = if (showBody) StreamContentDetails.thread(item) else emptyList(),
            ownActions = if (showBody) StreamContentDetails.ownActions(item) else emptyList(),
            conversation = StreamContentDetails.isConversation(item),
            person =
                if (showImages) {
                    StreamContentDetails.imageHandle(
                        item,
                        NotificationItemMapper.PERSON_ART_KEY,
                    )
                } else {
                    null
                },
            picture =
                if (showImages) {
                    StreamContentDetails.imageHandle(
                        item,
                        NotificationItemMapper.PICTURE_ART_KEY,
                    )
                } else {
                    null
                },
        )
    }

    private fun media(
        item: Item,
        privacy: StreamPrivacy,
        showBody: Boolean,
    ): MediaContent? {
        val app = item.appRef() ?: return null
        val controls =
            (item.ext[MediaItemExt.CONTROLS] as? ItemExtValue.Text)?.value.orEmpty()
                .split(',').mapNotNull(MediaControl::fromToken)
        return MediaContent(
            id = StreamContentId(item.id.value),
            app = app,
            appLabel = item.groupLabel,
            title = item.title,
            artist = item.subtitle,
            artwork = item.image,
            icon = item.icon,
            target = item.target,
            actions =
                buildSet {
                    if (item.actions.any { it is ItemAction.Open }) add(MediaAction.OPEN)
                    controls.forEach { control ->
                        add(
                            when (control) {
                                MediaControl.PLAY -> MediaAction.PLAY
                                MediaControl.PAUSE -> MediaAction.PAUSE
                                MediaControl.PREVIOUS -> MediaAction.PREVIOUS
                                MediaControl.NEXT -> MediaAction.NEXT
                                MediaControl.SEEK -> MediaAction.SEEK
                            },
                        )
                    }
                },
            isPlaying = (item.ext[MediaItemExt.PLAYING] as? ItemExtValue.Flag)?.value,
            positionMillis = (item.ext[MediaItemExt.POSITION] as? ItemExtValue.Number)?.value,
            durationMillis = (item.ext[MediaItemExt.DURATION] as? ItemExtValue.Number)?.value,
            queue = if (showBody) StreamContentDetails.queue(item) else emptyList(),
            postedAtEpochMillis = item.timeEpochMillis,
            privacy = privacy,
        )
    }

    private fun event(
        item: Item,
        privacy: StreamPrivacy,
        kind: EventKind,
    ): EventContent? {
        val start = item.timeEpochMillis ?: return null
        // A calendar feed puts the feed name in the subtitle when an event has no location; that is not a place.
        val location = item.subtitle?.takeIf { kind == EventKind.CALENDAR || it != item.groupLabel }
        return EventContent(
            id = StreamContentId(item.id.value),
            kind = kind,
            title = item.title,
            location = location,
            calendarName = item.groupLabel.takeIf { kind == EventKind.CALENDAR_FEED },
            startEpochMillis = start,
            endEpochMillis = (item.ext[END] as? ItemExtValue.Number)?.value,
            allDay = (item.ext[ALL_DAY] as? ItemExtValue.Flag)?.value ?: false,
            recurring = (item.ext[RECURRING] as? ItemExtValue.Flag)?.value ?: false,
            target = item.target,
            actions =
                if (item.actions.any { it is ItemAction.Open }) {
                    setOf(EventAction.OPEN, EventAction.EXPAND)
                } else {
                    setOf(EventAction.EXPAND)
                },
            privacy = privacy,
        )
    }

    private fun article(
        item: Item,
        privacy: StreamPrivacy,
    ): ArticleContent {
        val digest = (item.ext[ARTICLE_DIGEST] as? ItemExtValue.Text)?.value
        val read = (item.ext[ARTICLE_READ] as? ItemExtValue.Flag)?.value ?: false
        return ArticleContent(
            id = StreamContentId(item.id.value),
            feedId = item.groupKey ?: item.groupLabel.orEmpty(),
            feedName = item.groupLabel,
            title = item.title,
            summary = item.body,
            author = (item.ext[AUTHOR] as? ItemExtValue.Text)?.value,
            image = item.image,
            publishedAtEpochMillis = item.timeEpochMillis,
            stale = (item.ext[STALE] as? ItemExtValue.Flag)?.value ?: false,
            target = item.target,
            actions =
                buildSet {
                    if (item.actions.any { it is ItemAction.Open }) add(ArticleAction.OPEN)
                    if (digest != null && !read) add(ArticleAction.MARK_READ)
                },
            privacy = privacy,
            digest = digest,
            read = read,
        )
    }

    private fun recentApp(item: Item): RecentAppContent? {
        val app = item.appRef() ?: return null
        return RecentAppContent(
            id = StreamContentId(item.id.value),
            app = app,
            label = item.title,
            icon = item.icon,
            lastUsedEpochMillis = item.timeEpochMillis,
            target = item.target,
            actions = RecentAppAction.entries.toSet(),
            foregroundMillis = (item.ext[ItemExtKey("apps.foreground")] as? ItemExtValue.Number)?.value ?: 0L,
        )
    }

    private fun Item.appRef(): StreamAppRef? =
        (target as? ItemTarget.App)?.let { StreamAppRef(it.packageName, it.profileId) }
}

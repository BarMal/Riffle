package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemTarget

/**
 * The typed content the Stream draws (ADR 0003, Typed content). Like `Item` and `LauncherCard`, every value is
 * transient: its text, images and actions may be sensitive, so it must never be serialized into layout, settings,
 * backup or diagnostics. Nothing here has a codec.
 *
 * Values reach the UI already excluded (Hide rules), grouped, sorted, limited and redacted. A redacted value
 * ([privacy] is [StreamPrivacy.REDACTED] or the source's content level dropped a field) has the withheld text
 * fields set to null; identity, target, icon, time and group remain, as for a sensitive `Item`.
 */
sealed interface StreamContent {
    val id: StreamContentId
    val privacy: StreamPrivacy

    /** The time this content sorts and ages by: posted, start, published, due or last used. Null when none. */
    val timeEpochMillis: Long?
}

@JvmInline
value class StreamContentId(val value: String) {
    init {
        require(value.isNotBlank()) { "Stream content ids must not be blank." }
    }
}

/** [REDACTED]: the content is private (or its source is set to hide text); draw it as "Hidden content". */
enum class StreamPrivacy {
    VISIBLE,
    REDACTED,
}

/** The app a piece of content belongs to; [profileId] is null when the source does not know the profile. */
data class StreamAppRef(
    val packageName: String,
    val profileId: String? = null,
)

enum class NotificationAction {
    OPEN,
    DISMISS,
    REPLY,
    SNOOZE,

    /** Always offered: hiding writes an exclusion rule and needs nothing from the notification. */
    HIDE,
}

data class NotificationContent(
    override val id: StreamContentId,
    val app: StreamAppRef,
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val icon: ItemImageHandle?,
    val image: ItemImageHandle?,
    val postedAtEpochMillis: Long,
    val category: NotificationCategory?,
    val priority: NotificationPriority?,
    val target: ItemTarget,
    val actions: Set<NotificationAction>,
    override val privacy: StreamPrivacy,
) : StreamContent {
    override val timeEpochMillis: Long get() = postedAtEpochMillis
}

enum class MediaAction {
    OPEN,
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    SEEK,
}

/** One upcoming track. Optional: the platform media source does not provide a queue today, so it is empty. */
data class MediaQueueEntry(
    val title: String?,
    val artist: String? = null,
    val artwork: ItemImageHandle? = null,
)

/**
 * Now playing. [actions] holds what the source can really do: today the media source reads the media
 * notification, so only [MediaAction.OPEN] is offered, [isPlaying] and the positions are null and [queue] is
 * empty. A richer platform source fills them without any change to the UI contract.
 */
data class MediaContent(
    override val id: StreamContentId,
    val app: StreamAppRef,
    val appLabel: String?,
    val title: String?,
    val artist: String?,
    val artwork: ItemImageHandle?,
    val icon: ItemImageHandle?,
    val isPlaying: Boolean? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    val queue: List<MediaQueueEntry> = emptyList(),
    val target: ItemTarget,
    val actions: Set<MediaAction>,
    val postedAtEpochMillis: Long? = null,
    override val privacy: StreamPrivacy,
) : StreamContent {
    override val timeEpochMillis: Long? get() = postedAtEpochMillis
}

enum class EventKind {
    CALENDAR,
    CALENDAR_FEED,
    ALARM,
}

enum class EventAction {
    OPEN,
    EXPAND,
}

/**
 * A calendar event, a calendar-feed event or the next alarm. [endEpochMillis] is null for an alarm and for an
 * event whose private details were withheld. [calendarName] is the feed name for a calendar feed.
 */
data class EventContent(
    override val id: StreamContentId,
    val kind: EventKind,
    val title: String?,
    val location: String?,
    val calendarName: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val allDay: Boolean,
    val recurring: Boolean,
    val target: ItemTarget,
    val actions: Set<EventAction>,
    override val privacy: StreamPrivacy,
) : StreamContent {
    override val timeEpochMillis: Long get() = startEpochMillis
}

enum class ArticleAction {
    OPEN,

    /** Declared for the Stream UI; there is no read state yet, so no source offers it today. */
    MARK_READ,
}

data class ArticleContent(
    override val id: StreamContentId,
    val feedId: String,
    val feedName: String?,
    val title: String?,
    val summary: String?,
    val author: String?,
    val image: ItemImageHandle?,
    val publishedAtEpochMillis: Long?,
    val stale: Boolean,
    val target: ItemTarget,
    val actions: Set<ArticleAction>,
    override val privacy: StreamPrivacy,
) : StreamContent {
    override val timeEpochMillis: Long? get() = publishedAtEpochMillis
}

enum class TaskAction {
    COMPLETE,
    OPEN,
}

/** One to-do. There is no source yet (P10 adds Todoist); see [TaskProvider]. */
data class TaskContent(
    override val id: StreamContentId,
    val title: String?,
    val listName: String? = null,
    val dueEpochMillis: Long? = null,
    /** The due value carries a time of day, not only a date. */
    val dueHasTime: Boolean = false,
    val completed: Boolean = false,
    val target: ItemTarget = ItemTarget.None,
    val actions: Set<TaskAction> = setOf(TaskAction.COMPLETE, TaskAction.OPEN),
    override val privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
) : StreamContent {
    override val timeEpochMillis: Long? get() = dueEpochMillis
}

enum class RecentAppAction {
    LAUNCH,
    SHORTCUTS,
    ADD_TO_HOME,
    PIN_TO_DOCK,
    HIDE,
}

data class RecentAppContent(
    override val id: StreamContentId,
    val app: StreamAppRef,
    val label: String?,
    val icon: ItemImageHandle?,
    val lastUsedEpochMillis: Long?,
    val target: ItemTarget,
    val actions: Set<RecentAppAction>,
    override val privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
) : StreamContent {
    override val timeEpochMillis: Long? get() = lastUsedEpochMillis
}

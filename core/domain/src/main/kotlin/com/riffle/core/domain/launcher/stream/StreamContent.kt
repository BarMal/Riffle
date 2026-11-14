package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppIdentity
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

/** One message of a notification thread. Transient and only present on content that is not redacted. */
data class NotificationMessage(
    val sender: String?,
    val text: String,
    val timestampEpochMillis: Long,
)

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
    /** The platform key the action gateway acts on; null when redacted (no action but hide is possible then). */
    val notificationKey: String? = null,
    /** Message history of a messaging notification, oldest first; empty for anything else and when redacted. */
    val thread: List<NotificationMessage> = emptyList(),
    /** The contact photo the app published (its person icon); null when it published none, and when redacted. */
    val person: ItemImageHandle? = null,
    /** The picture the notification carries (a big-picture style); null when it has none, and when redacted. */
    val picture: ItemImageHandle? = null,
    /** The notification is a conversation with a person (a messaging style or the message category). */
    val conversation: Boolean = false,
) : StreamContent {
    override val timeEpochMillis: Long get() = postedAtEpochMillis

    /**
     * Whether there is a person to show: a conversation, or a contact photo. Only then does a card draw a round avatar
     * of a photo or initials with the app's icon badged on it; any other notification (a promotion, a download) has the
     * app's icon and no initials.
     */
    val hasPerson: Boolean get() = conversation || person != null || thread.isNotEmpty()

    /**
     * The round avatar of a card: the contact photo, else the notification's large icon (which a messaging app sets to
     * the conversation's photo); null when the app published neither (the card draws initials or the app's icon).
     */
    val avatar: ItemImageHandle? get() = person ?: image
}

enum class MediaAction {
    OPEN,
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    SEEK,
}

/** One upcoming track; empty queue when the session exposes none. */
data class MediaQueueEntry(
    val title: String?,
    val artist: String? = null,
    val artwork: ItemImageHandle? = null,
)

/**
 * Now playing. [actions] holds what the session can really do right now (transport actions only while the session
 * allows them); [isPlaying], the positions and [queue] are null or empty when the session reports none. Never
 * persisted. A redacted card has no queue and no actions beyond what the privacy level lets through.
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

    /** Offered while the article is unread; backed by the RSS cache's read state. */
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
    /** Opaque article digest the read state is keyed by; null when redacted. */
    val digest: String? = null,
    val read: Boolean = false,
) : StreamContent {
    override val timeEpochMillis: Long? get() = publishedAtEpochMillis
}

enum class TaskAction {
    COMPLETE,
    OPEN,
}

/**
 * One to-do (Todoist, see [TodoistTaskProvider]). [priority] is 1 (normal) to 4 (urgent). [stale] says the list it
 * came from could not be refreshed and is the last one fetched.
 */
data class TaskContent(
    override val id: StreamContentId,
    val title: String?,
    val listName: String? = null,
    val dueEpochMillis: Long? = null,
    /** The due value carries a time of day, not only a date. */
    val dueHasTime: Boolean = false,
    val completed: Boolean = false,
    val overdue: Boolean = false,
    val priority: Int = 1,
    val stale: Boolean = false,
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
    /** Foreground time within the usage window; zero when unknown. Ranks frequent apps. */
    val foregroundMillis: Long = 0L,
) : StreamContent {
    override val timeEpochMillis: Long? get() = lastUsedEpochMillis
}

enum class AppShortcutAction {
    LAUNCH,
}

/**
 * One launcher shortcut of an app (static, dynamic or pinned), as a card of a pinned app's shortcuts page: the
 * shortcut's icon, short label and, when it differs, long label. A tap launches it. [shortcutId] is null for the
 * "Open <app>" card an app with no shortcuts (or no launcher role) has instead; that card opens the app. Never
 * persisted; it is built from the shortcuts the launcher already read for the Dock's long-press menu.
 */
data class AppShortcutContent(
    override val id: StreamContentId,
    val app: StreamAppRef,
    val appIdentity: AppIdentity,
    val appLabel: String?,
    val shortcutId: String?,
    val shortLabel: String?,
    val longLabel: String?,
    val icon: ItemImageHandle?,
    val enabled: Boolean = true,
    val disabledMessage: String? = null,
    val actions: Set<AppShortcutAction> = setOf(AppShortcutAction.LAUNCH),
    override val privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
) : StreamContent {
    override val timeEpochMillis: Long? get() = null

    /** True for the card that opens the app itself. */
    val opensApp: Boolean get() = shortcutId == null
}

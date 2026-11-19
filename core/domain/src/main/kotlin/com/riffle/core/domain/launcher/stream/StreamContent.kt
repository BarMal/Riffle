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
    /** Who wrote it; null: the user. */
    val sender: String?,
    val text: String,
    val timestampEpochMillis: Long,
    /** False when saying [sender] would only repeat the card's title ([ConversationLines]): the text stands alone. */
    val showSender: Boolean = true,
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
    /**
     * The notification's own action buttons ("Mark as read", "Archive", "Like"): at most three, in its order, without
     * the inline reply. Ids and titles only; the platform intents stay with the listener. Empty when redacted.
     */
    val ownActions: List<NotificationOwnAction> = emptyList(),
    /**
     * What was read out of the notification's own layout, for a notification that fills in no title or text (see
     * [CustomNotificationContent]); null for any other, and when redacted.
     */
    val custom: CustomNotificationContent? = null,
    /**
     * The notification is going on now and its app keeps it up to date ([LiveNotificationRule]): its own action
     * buttons are on the browse card too.
     */
    val live: Boolean = false,
) : StreamContent {
    override val timeEpochMillis: Long get() = postedAtEpochMillis

    /**
     * Whether there is a person to show: a conversation, or a contact photo. Only then does a card draw a round avatar
     * of a photo or initials with the app's icon badged on it; any other notification (a promotion, a download) has the
     * app's icon and no initials.
     */
    val hasPerson: Boolean get() = conversation || person != null || thread.isNotEmpty()

    /** The content is withheld: the Privacy content level of its source hides text, or its profile is quiet. */
    val hidden: Boolean get() = privacy == StreamPrivacy.REDACTED

    /**
     * The card's title line: the notification's own title, else (it published none) the app's name. Null only when the
     * content is [hidden]: only then does a card say "Hidden content". A notification with no title and no text is not
     * hidden, it is a notification from its app.
     */
    val headline: String?
        get() =
            title?.takeIf { it.isNotBlank() }
                ?: if (hidden) null else custom?.title ?: appLabel?.takeIf { it.isNotBlank() } ?: app.packageName

    /**
     * What the card draws under its header, best first: the conversation, else the text the app filled in, else what
     * its own layout says (extracted, or embedded when it has no text).
     */
    val body: NotificationBody
        get() =
            when {
                thread.isNotEmpty() -> NotificationBody.Thread(threadLines)
                !text.isNullOrBlank() -> NotificationBody.Text(text)
                custom != null && (custom.embed || custom.lines.isNotEmpty() || custom.bigLines.isNotEmpty()) ->
                    NotificationBody.Custom(custom)
                else -> NotificationBody.None
            }

    /**
     * The message history as the card writes it: each line says who wrote it unless that would only repeat the card's
     * title or the conversation's ([ConversationLines]).
     */
    val threadLines: List<NotificationMessage> get() = ConversationLines.resolve(thread, listOf(headline, title))

    /** The app's name as the card's second line; null when the title line already is the app's name. */
    val secondaryLabel: String? get() = appLabel?.takeIf { it.isNotBlank() && it != headline }

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

    /** Offered by a [MediaContent.source] only: forget this recent player (a swipe, the Dismiss button). */
    DISMISS,

    /** Offered by a [MediaContent.queueItem] only: play this track now (skip the session to it). */
    PLAY_QUEUE_ITEM,
}

/**
 * One upcoming track of an active player's queue, as the card behind the player's own: the platform's [queueId] (what
 * playing it names, and what keeps the card the same card while the queue moves) and its [position] after the track
 * playing (0 is next).
 */
data class MediaQueueItem(
    val queueId: Long,
    val position: Int,
)

/**
 * Now playing. [actions] holds what the session can really do right now (transport actions only while the session
 * allows them); [isPlaying] and the positions are null when the session reports none. Never persisted. A redacted card
 * has no queue cards and no actions beyond what the privacy level lets through.
 *
 * A [queueItem] is one upcoming track of the player's queue (ADR 0003, Amendment 16): a card behind the player's own,
 * with the track's [title] and [artist] (and [artwork] when the queue gives one), the app's [icon] and [appLabel], and
 * one action, [MediaAction.PLAY_QUEUE_ITEM]. It is the same kind of content as the player (same app, same privacy
 * rules), not a ninth kind.
 *
 * A [source] is a recent player (ADR 0003, Amendment 4): an app that played lately and has no session now. It is still
 * media content (the quiet card of an app, its last [title] and [artist] when this process saw them, [postedAtEpochMillis]
 * the time it last played) with no controls: [MediaAction.PLAY] means "resume it", offered only when the app can be
 * resumed; [MediaAction.OPEN] opens the app; [MediaAction.DISMISS] forgets it.
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
    val target: ItemTarget,
    val actions: Set<MediaAction>,
    val postedAtEpochMillis: Long? = null,
    override val privacy: StreamPrivacy,
    val source: Boolean = false,
    val queueItem: MediaQueueItem? = null,
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

/** What a card of the Pairs stack is: a saved pair, the one being made, or the "New pair" card. */
enum class PairCardKind {
    /** A stored pair of two apps. */
    SAVED,

    /** The pair being made: each half is an app or still "Choose an app". Never stored until both are chosen. */
    DRAFT,

    /** The "New pair" card: at the back of the stack, and alone on an empty page. */
    NEW,
}

/** What a card of the Pairs stack offers (see [AppPairContent.actions]). */
enum class PairAction {
    /** Open the two apps side by side. */
    OPEN,
    SWAP_SIDES,
    CHANGE_FIRST,
    CHANGE_SECOND,

    /** Remove the pair (a swipe, the menu, the accessibility action); Undo brings it back. */
    REMOVE,

    /** The draft's halves: choose the app of that side. */
    CHOOSE_FIRST,
    CHOOSE_SECOND,

    /** The "New pair" card: put a draft card in front. */
    NEW_PAIR,
}

/** One app of a pair: which, its name and icon (resolved from the installed apps when the content is made). */
data class PairSide(
    val identity: AppIdentity,
    val label: String,
    val icon: ItemImageHandle?,
) {
    val app: StreamAppRef get() = StreamAppRef(identity.packageName.value, identity.profile.id.value)
}

/**
 * A card of the Pairs stack (ADR 0003, Amendment 7): two apps side by side, [first] on the start (left) pane. Its own
 * kind of content, with its own card, strip and actions; never persisted as content (what is stored is the two apps
 * and their order, `AppPair`). A [PairCardKind.DRAFT] has no [pairId] and a null half where the app is not chosen
 * yet; the [PairCardKind.NEW] card has neither half. [timeEpochMillis] is when the pair was last opened.
 */
data class AppPairContent(
    override val id: StreamContentId,
    val kind: PairCardKind,
    val pairId: String?,
    val first: PairSide?,
    val second: PairSide?,
    val actions: Set<PairAction>,
    val lastOpenedEpochMillis: Long? = null,
    override val privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
) : StreamContent {
    override val timeEpochMillis: Long? get() = lastOpenedEpochMillis
}

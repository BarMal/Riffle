package com.riffle.app.launcher.diagnostics

import android.media.session.PlaybackState
import com.riffle.core.domain.launcher.apps.InstalledApp

/*
 * The shape of what an app publishes to a launcher, for the "What apps publish" diagnostics page.
 *
 * Everything here is plain Kotlin so it can be tested on the JVM. The platform side (AndroidAppPublishSource) copies
 * raw values into the *Input types and the mappers below reduce them to shapes: presence flags, counts and app-defined
 * UI labels. Notification text, message bodies and contact names go in the inputs and never come out of a
 * mapper, so neither the screen nor the copied report can contain them.
 */

/** Why a section could not be read. */
enum class SectionProblem {
    LAUNCHER_ROLE_MISSING,
    NOTIFICATION_ACCESS_MISSING,
    UNAVAILABLE,
}

/** One section of a report: its items, or why it could not be read. No items and no problem means empty. */
data class PublishSection<T>(
    val items: List<T> = emptyList(),
    val problem: SectionProblem? = null,
)

data class ShortcutInput(
    val shortLabel: CharSequence?,
    val longLabel: CharSequence?,
    val isStatic: Boolean,
    val isDynamic: Boolean,
    val isPinned: Boolean,
    val isCached: Boolean,
)

data class ShortcutShape(
    val shortLabel: String,
    val longLabel: String?,
    val types: List<String>,
)

fun ShortcutInput.toShape(): ShortcutShape =
    ShortcutShape(
        shortLabel = shortLabel?.toString().orEmpty(),
        longLabel = longLabel?.toString()?.takeIf(String::isNotBlank),
        types =
            buildList {
                if (isStatic) add("static")
                if (isDynamic) add("dynamic")
                if (isPinned) add("pinned")
                if (isCached) add("cached")
            },
    )

data class ActionInput(
    val title: CharSequence?,
    val hasRemoteInput: Boolean,
)

data class MessageInput(
    val text: CharSequence?,
    val senderName: CharSequence?,
    val hasPerson: Boolean,
    val hasPersonIcon: Boolean,
)

/** Raw values read from one posted notification. Transient: only [toShape] output is ever shown or copied. */
data class NotificationInput(
    val category: String?,
    val isOngoing: Boolean,
    val template: String?,
    val title: CharSequence?,
    val text: CharSequence?,
    val hasLargeIcon: Boolean,
    val hasBigPicture: Boolean,
    val hasContentView: Boolean,
    val hasBigContentView: Boolean,
    val hasHeadsUpContentView: Boolean,
    /** Null when the notification is not a MessagingStyle one. */
    val messages: List<MessageInput>?,
    val progressMax: Int,
    val progress: Int,
    val progressIndeterminate: Boolean,
    val actions: List<ActionInput>,
)

data class ProgressShape(
    val max: Int,
    val current: Int,
    val indeterminate: Boolean,
)

data class ActionShape(
    val title: String,
    val hasRemoteInput: Boolean,
)

data class NotificationShape(
    val category: String?,
    val isOngoing: Boolean,
    val template: String?,
    val hasTitle: Boolean,
    val hasText: Boolean,
    val hasLargeIcon: Boolean,
    val hasBigPicture: Boolean,
    val hasCustomContentView: Boolean,
    val hasCustomBigContentView: Boolean,
    val hasCustomHeadsUpView: Boolean,
    val messageCount: Int?,
    val messagesWithPerson: Int,
    val messagesWithPersonIcon: Int,
    val progress: ProgressShape?,
    val actions: List<ActionShape>,
)

fun NotificationInput.toShape(): NotificationShape =
    NotificationShape(
        category = category?.takeIf(String::isNotBlank),
        isOngoing = isOngoing,
        template = templateShortName(template),
        hasTitle = !title.isNullOrBlank(),
        hasText = !text.isNullOrBlank(),
        hasLargeIcon = hasLargeIcon,
        hasBigPicture = hasBigPicture,
        hasCustomContentView = hasContentView,
        hasCustomBigContentView = hasBigContentView,
        hasCustomHeadsUpView = hasHeadsUpContentView,
        messageCount = messages?.size,
        messagesWithPerson = messages.orEmpty().count { it.hasPerson },
        messagesWithPersonIcon = messages.orEmpty().count { it.hasPersonIcon },
        progress =
            if (progressMax > 0 || progressIndeterminate) {
                ProgressShape(max = progressMax, current = progress, indeterminate = progressIndeterminate)
            } else {
                null
            },
        // Action titles are app-defined UI ("Reply", "Mark as read"), shown on purpose.
        actions =
            actions.map { ActionShape(title = it.title?.toString().orEmpty(), hasRemoteInput = it.hasRemoteInput) },
    )

/** "android.app.Notification$MessagingStyle" becomes "MessagingStyle". */
fun templateShortName(template: String?): String? =
    template?.substringAfterLast('$')?.substringAfterLast('.')?.takeIf(String::isNotBlank)

/** Which metadata keys a media session sets. Presence only. */
data class MediaMetadataPresence(
    val title: Boolean,
    val artist: Boolean,
    val album: Boolean,
    val duration: Boolean,
    val artBitmap: Boolean,
    val artUri: Boolean,
)

data class QueueItemShape(
    val title: String?,
    val hasIconBitmap: Boolean,
    val hasIconUri: Boolean,
)

data class MediaShape(
    val metadata: MediaMetadataPresence,
    val playbackState: String,
    val actions: List<String>,
    val customActions: List<String>,
    val queueSize: Int,
    val queueItems: List<QueueItemShape>,
)

const val MAX_QUEUE_ITEMS_SHOWN = 10

data class WidgetShape(
    val label: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
)

data class AppPublishReport(
    val appLabel: String,
    val packageName: String,
    val shortcuts: PublishSection<ShortcutShape>,
    val notifications: PublishSection<NotificationShape>,
    val media: PublishSection<MediaShape>,
    val widgets: PublishSection<WidgetShape>,
)

private val PLAYBACK_ACTION_NAMES: List<Pair<Long, String>> =
    listOf(
        PlaybackState.ACTION_STOP to "stop",
        PlaybackState.ACTION_PAUSE to "pause",
        PlaybackState.ACTION_PLAY to "play",
        PlaybackState.ACTION_REWIND to "rewind",
        PlaybackState.ACTION_SKIP_TO_PREVIOUS to "skip to previous",
        PlaybackState.ACTION_SKIP_TO_NEXT to "skip to next",
        PlaybackState.ACTION_FAST_FORWARD to "fast forward",
        PlaybackState.ACTION_SET_RATING to "set rating",
        PlaybackState.ACTION_SEEK_TO to "seek to",
        PlaybackState.ACTION_PLAY_PAUSE to "play/pause",
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID to "play from media id",
        PlaybackState.ACTION_PLAY_FROM_SEARCH to "play from search",
        PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM to "skip to queue item",
        PlaybackState.ACTION_PLAY_FROM_URI to "play from uri",
        PlaybackState.ACTION_PREPARE to "prepare",
        PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID to "prepare from media id",
        PlaybackState.ACTION_PREPARE_FROM_SEARCH to "prepare from search",
        PlaybackState.ACTION_PREPARE_FROM_URI to "prepare from uri",
        PlaybackState.ACTION_SET_PLAYBACK_SPEED to "set playback speed",
    )

/** The transport actions set in a `PlaybackState.getActions()` bitfield, in bit order. */
fun decodePlaybackActions(actions: Long): List<String> =
    PLAYBACK_ACTION_NAMES.filter { (bit, _) -> actions and bit != 0L }.map { (_, name) -> name }

private val PLAYBACK_STATE_NAMES: Map<Int, String> =
    mapOf(
        PlaybackState.STATE_NONE to "none",
        PlaybackState.STATE_STOPPED to "stopped",
        PlaybackState.STATE_PAUSED to "paused",
        PlaybackState.STATE_PLAYING to "playing",
        PlaybackState.STATE_FAST_FORWARDING to "fast forwarding",
        PlaybackState.STATE_REWINDING to "rewinding",
        PlaybackState.STATE_BUFFERING to "buffering",
        PlaybackState.STATE_ERROR to "error",
        PlaybackState.STATE_CONNECTING to "connecting",
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS to "skipping to previous",
        PlaybackState.STATE_SKIPPING_TO_NEXT to "skipping to next",
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM to "skipping to queue item",
    )

fun playbackStateName(state: Int?): String =
    if (state == null) "no playback state" else PLAYBACK_STATE_NAMES[state] ?: "unknown ($state)"

/**
 * The apps to list: one entry per package and profile, filtered by [query] (label or package name), with the apps in
 * [livePackages] (a notification or media session right now) first, then by label.
 */
fun orderPublishApps(
    apps: List<InstalledApp>,
    livePackages: Set<String>,
    query: String,
): List<InstalledApp> {
    val needle = query.trim()
    return apps
        .distinctBy { it.identity.packageName.value to it.identity.profile.id.value }
        .filter { app ->
            needle.isEmpty() ||
                app.label.contains(needle, ignoreCase = true) ||
                app.identity.packageName.value.contains(needle, ignoreCase = true)
        }.sortedWith(
            compareBy<InstalledApp> { it.identity.packageName.value !in livePackages }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
}

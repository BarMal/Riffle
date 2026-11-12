package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.home.MAX_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.home.MIN_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamPrivacy

/**
 * One entry of the Dock's dynamic side (ADR 0003, The Dock is the handle). Transient, derived from what the Stream
 * shows, so Hide rules and content levels have already applied; never stored.
 */
sealed interface DockDynamicSideEntry {
    /** A stable key for the entry (tests, list keys, spotlight sources). */
    val key: String

    /**
     * An app with something new: its notifications group in the Stream's Notifications section. [groupKey] is that
     * group's key (`package:profile`), which a tap lands the Stream on; [count] is how many it has.
     */
    data class LiveApp(
        val groupKey: String,
        val packageName: String,
        val profileId: String?,
        val label: String?,
        val count: Int,
    ) : DockDynamicSideEntry {
        override val key: String get() = "live:$groupKey"
    }

    /** The one now-playing entry, while media is active; [title] is null when the content is withheld. */
    data class NowPlaying(
        val packageName: String,
        val profileId: String?,
        val appLabel: String?,
        val title: String?,
    ) : DockDynamicSideEntry {
        override val key: String get() = "now"
    }

    /** The Stream button: always last, so the handle exists when nothing is live. */
    data object StreamButton : DockDynamicSideEntry {
        override val key: String get() = "stream"
    }
}

/** Which entries the Dock's dynamic side shows, and in which order. Pure. */
object DockDynamicSide {
    /** Entries the side holds besides live apps: now playing and the Stream button. */
    const val FIXED_ENTRY_COUNT = 2

    /**
     * The side's entries: up to [liveAppSlots] live apps (newest first), then now playing when [media] is active, then
     * the Stream button, always last.
     *
     * [notificationGroups] are the groups of the Stream's Notifications section (so Hide rules and privacy have
     * applied); a group with no notification content is skipped. Live apps are ordered by their newest notification,
     * newest first, ties by group key.
     */
    fun entries(
        notificationGroups: List<StreamGroup>,
        media: MediaContent?,
        liveAppSlots: Int,
    ): List<DockDynamicSideEntry> {
        val slots = liveAppSlots.coerceIn(MIN_DOCK_LIVE_APP_SLOTS, MAX_DOCK_LIVE_APP_SLOTS)
        val live =
            notificationGroups
                .mapNotNull(::liveApp)
                .sortedWith(newestFirst)
                .map { it.second }
                .take(slots)
        return live + listOfNotNull(media?.let(::nowPlaying)) + DockDynamicSideEntry.StreamButton
    }

    private val newestFirst =
        compareByDescending<Pair<Long, DockDynamicSideEntry.LiveApp>> { it.first }.thenBy { it.second.groupKey }

    private fun liveApp(group: StreamGroup): Pair<Long, DockDynamicSideEntry.LiveApp>? {
        val notifications = group.items.filterIsInstance<NotificationContent>()
        val newest = notifications.maxByOrNull { it.postedAtEpochMillis } ?: return null
        return newest.postedAtEpochMillis to
            DockDynamicSideEntry.LiveApp(
                groupKey = group.key,
                packageName = newest.app.packageName,
                profileId = newest.app.profileId,
                label = group.label ?: newest.appLabel,
                count = group.totalCount.coerceAtLeast(notifications.size),
            )
    }

    private fun nowPlaying(media: MediaContent): DockDynamicSideEntry.NowPlaying =
        DockDynamicSideEntry.NowPlaying(
            packageName = media.app.packageName,
            profileId = media.app.profileId,
            appLabel = media.appLabel,
            title = media.title.takeIf { media.privacy == StreamPrivacy.VISIBLE },
        )
}

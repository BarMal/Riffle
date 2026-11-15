package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.home.MAX_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.home.MIN_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamPageRef
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection

/**
 * One entry of the Dock's dynamic side (ADR 0003, Amendment 3). Transient, derived from what the Stream shows, so Hide
 * rules and content levels have already applied; never stored.
 *
 * In the Home mode the side holds live apps and, while media is active, the Now tile; in the contextual mode the same
 * entries are followed by one [ContextTile] per Stream page that is not an app (Now's tile is always among them: it is
 * the [NowPlaying] entry while media is active, else the plain tile).
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
        override val key: String get() = liveAppKey(groupKey)
    }

    /**
     * The Now tile while media is active (ADR 0003, Amendment 4): the same tile as [ContextTile] of Now (same key), the
     * Now page's own play-glyph tile, marked as playing. It is not an app's icon: the playing app's own Dock icon, if
     * it has notifications, is a live app and leads to that app's notifications page. [title] is null when the
     * content is withheld.
     */
    data class NowPlaying(val title: String?) : DockDynamicSideEntry {
        override val key: String get() = contextTileKey(StreamSection.NOW)
    }

    /**
     * A contextual source ([section]: Now, Agenda, Tasks, Recents, Feeds, or Notifications while it holds only a state)
     * that exists only in the contextual mode, after the live apps.
     */
    data class ContextTile(val section: StreamSection) : DockDynamicSideEntry {
        override val key: String get() = contextTileKey(section)
    }

    companion object {
        /** What a live app's key starts with, then its notifications group's key. */
        const val LIVE_APP_KEY_PREFIX: String = "live:"

        /** What a contextual tile's key starts with, then its section's stored id. */
        const val CONTEXT_TILE_KEY_PREFIX: String = "ctx:"

        /** What a pinned app's key starts with, then its app key (`package:profile`). */
        const val PINNED_APP_KEY_PREFIX: String = "pin:"

        /** The key of the pinned app whose app key is [appKey]. */
        fun pinnedAppKey(appKey: String): String = "$PINNED_APP_KEY_PREFIX$appKey"

        /** The key of the live app whose notifications group is [groupKey]. */
        fun liveAppKey(groupKey: String): String = "$LIVE_APP_KEY_PREFIX$groupKey"

        /** The key of the contextual tile of [section]. */
        fun contextTileKey(section: StreamSection): String = "$CONTEXT_TILE_KEY_PREFIX${section.storedId}"
    }
}

/** Which entries the Dock's dynamic side shows, and in which order. Pure. */
object DockDynamicSide {
    /** Entries the Home mode holds besides live apps: now playing. */
    const val FIXED_ENTRY_COUNT = 1

    /**
     * The side's entries: up to [liveAppSlots] live apps (newest first), then the Now tile when [media] is active, then
     * (the contextual mode only) one [DockDynamicSideEntry.ContextTile] for each of [contextSections], in that order.
     * The Home mode passes none, so it holds no tile; the entries it shares with the contextual mode keep their order.
     *
     * An app is drawn once: a group whose key is in [pinnedGroupKeys] (`package:profile` of a pinned app) has no live
     * entry, the pinned icon stands for its page (and does not use up a live slot).
     *
     * [notificationGroups] are the groups of the Stream's Notifications section (so Hide rules and privacy have
     * applied); a group with no notification content is skipped. Live apps are ordered by their newest notification,
     * newest first, ties by group key.
     */
    fun entries(
        notificationGroups: List<StreamGroup>,
        media: MediaContent?,
        liveAppSlots: Int,
        contextSections: List<StreamSection> = emptyList(),
        pinnedGroupKeys: Set<String> = emptySet(),
    ): List<DockDynamicSideEntry> {
        val slots = liveAppSlots.coerceIn(MIN_DOCK_LIVE_APP_SLOTS, MAX_DOCK_LIVE_APP_SLOTS)
        val live =
            notificationGroups
                .filter { group -> group.key !in pinnedGroupKeys }
                .mapNotNull(::liveApp)
                .sortedWith(newestFirst)
                .map { it.second }
                .take(slots)
        return live + listOfNotNull(media?.let(::nowPlaying)) + contextSections.map(DockDynamicSideEntry::ContextTile)
    }

    /**
     * The sections that get a contextual tile, in Dock order, from the pages the Stream is showing (in the order
     * [DockPageOrder] gave them): one per section that has a page that is not an app's, so a section with several pages
     * (one per feed) has one tile. Now is not repeated while its marked tile is among the entries ([nowPlaying]).
     */
    fun contextSections(
        pages: List<StreamPageRef>,
        nowPlaying: Boolean,
    ): List<StreamSection> =
        pages
            .filterNot(StreamPageRef::isAppPage)
            .map(StreamPageRef::section)
            .distinct()
            .filterNot { section -> nowPlaying && section == StreamSection.NOW }

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
        DockDynamicSideEntry.NowPlaying(title = media.title.takeIf { media.privacy == StreamPrivacy.VISIBLE })
}

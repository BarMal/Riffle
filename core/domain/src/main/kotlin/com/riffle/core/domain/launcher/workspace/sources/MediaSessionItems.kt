package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.stream.RecentPlayerSource
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/** What a transport control of a media session can do right now. */
enum class MediaControl(val token: String) {
    PLAY("play"),
    PAUSE("pause"),
    PREVIOUS("previous"),
    NEXT("next"),
    SEEK("seek"),
    ;

    companion object {
        fun fromToken(token: String): MediaControl? = entries.firstOrNull { it.token == token }
    }
}

/**
 * One upcoming track of a session's queue. Text is user content: transient, never persisted or logged. [queueId] is the
 * platform's queue item id (what skipping to it names); [hasArtwork] says a thumbnail is available behind the
 * platform loader, as for the session's own artwork.
 */
data class MediaSessionQueueEntry(
    val title: String?,
    val artist: String? = null,
    val queueId: Long = 0L,
    val hasArtwork: Boolean = false,
)

/**
 * One active media session as the platform reports it. Transient: never persisted, logged or put in
 * diagnostics. [hasArtwork] only says a bitmap is available; the pixels stay behind the platform loader.
 */
data class MediaSessionInfo(
    val packageName: String,
    val title: String? = null,
    val artist: String? = null,
    val hasArtwork: Boolean = false,
    /** Null when the session reports no playback state. */
    val isPlaying: Boolean? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    val controls: Set<MediaControl> = emptySet(),
    val queue: List<MediaSessionQueueEntry> = emptyList(),
)

/** Platform seam: the active media sessions. Blocking; call off the main thread. Empty when there is no access. */
fun interface MediaSessionReader {
    fun activeSessions(): List<MediaSessionInfo>
}

/**
 * Ext keys the media source adds to its items; the Stream reads them back into `MediaContent`. The queue is not part of
 * the player's item: each upcoming track is an item of its own (see [MediaSessionItems]).
 */
object MediaItemExt {
    val PLAYING = ItemExtKey("media.playing")
    val POSITION = ItemExtKey("media.position")
    val DURATION = ItemExtKey("media.duration")
    val CONTROLS = ItemExtKey("media.controls")

    /** Set on the item of one upcoming track of a player's queue: the platform's queue item id. */
    val QUEUE_ID = ItemExtKey("media.queue_id")

    /** Where the track comes in the queue after the one playing: 0 is next. */
    val QUEUE_POSITION = ItemExtKey("media.queue_position")

    /** Set on the item of a recent player that has no session (a "source"). */
    val SOURCE = ItemExtKey("media.source")

    /** Set on a source whose app can be resumed from here (it declares a media browser service). */
    val RESUMABLE = ItemExtKey("media.resumable")

    /** The thumbnail of one queue entry; the platform keeps the bitmap in memory only, with the session's artwork. */
    fun queueArtwork(
        packageName: String,
        queueId: Long,
    ): ItemImageHandle = ItemImageHandle("media-art:${queueArtworkKey(packageName, queueId)}")

    /** The in-memory key the artwork store holds a queue entry's thumbnail under. */
    fun queueArtworkKey(
        packageName: String,
        queueId: Long,
    ): String = "$packageName/q/$queueId"

    /** The image handle of a session's artwork; the platform keeps the bitmap in memory only. */
    fun artwork(packageName: String): ItemImageHandle = ItemImageHandle("media-art:$packageName")

    const val MAX_QUEUE = 10
}

/**
 * Adds live session data (play state, position, duration, transport controls, artwork) to the media items the
 * notification source already produced, and one item per upcoming track of its queue (at most [MediaItemExt.MAX_QUEUE],
 * in queue order, after the one playing). It only enriches: a session whose package has no visible media item is
 * ignored, so the hide rules, profile visibility and the stale filter that built the items still decide what exists,
 * and a [ItemPrivacy.SENSITIVE] item is never enriched and has no queue items. Existing text is kept; a session only
 * fills a blank. A queue item carries its player's group and target, so a Hide rule for the app hides them too.
 */
object MediaSessionItems {
    fun enrich(
        items: List<Item>,
        sessions: List<MediaSessionInfo>,
    ): List<Item> {
        if (sessions.isEmpty()) return items
        val byPackage = sessions.distinctBy { it.packageName }.associateBy { it.packageName }
        val queued = HashSet<String>()
        val queueItems = ArrayList<Item>()
        val enriched =
            items.map { item ->
                val session = (item.target as? ItemTarget.App)?.let { byPackage[it.packageName] }
                if (session == null || item.privacy == ItemPrivacy.SENSITIVE) {
                    item
                } else {
                    val player = item.enrichedWith(session)
                    // One queue per session: a second notification of the same app does not repeat it.
                    if (queued.add(session.packageName)) queueItems += player.queueItems(session)
                    player
                }
            }
        return enriched + queueItems
    }

    /** The upcoming tracks of [session] as items of their own, in queue order. */
    private fun Item.queueItems(session: MediaSessionInfo): List<Item> {
        val packageName = (target as ItemTarget.App).packageName
        return session.queue
            .filter { !it.title.isNullOrBlank() }
            .take(MediaItemExt.MAX_QUEUE)
            .mapIndexed { position, entry ->
                Item(
                    id = ItemId("${id.value}:q:${entry.queueId}"),
                    sourceId = sourceId,
                    target = target,
                    title = entry.title,
                    subtitle = entry.artist,
                    icon = icon,
                    image = MediaItemExt.queueArtwork(packageName, entry.queueId).takeIf { entry.hasArtwork },
                    timeEpochMillis = timeEpochMillis,
                    groupKey = groupKey,
                    groupLabel = groupLabel,
                    actions = listOf(ItemAction.Open()),
                    privacy = privacy,
                    ext =
                        ext.filterKeys { !it.value.startsWith("media.") } +
                            mapOf(
                                MediaItemExt.QUEUE_ID to ItemExtValue.Number(entry.queueId),
                                MediaItemExt.QUEUE_POSITION to ItemExtValue.Number(position.toLong()),
                            ),
                )
            }
    }

    private fun Item.enrichedWith(session: MediaSessionInfo): Item {
        val ext = LinkedHashMap(ext)
        session.isPlaying?.let { ext[MediaItemExt.PLAYING] = ItemExtValue.Flag(it) }
        session.positionMillis?.takeIf { it >= 0L }?.let { ext[MediaItemExt.POSITION] = ItemExtValue.Number(it) }
        session.durationMillis?.takeIf { it > 0L }?.let { ext[MediaItemExt.DURATION] = ItemExtValue.Number(it) }
        val controls = session.controls.sortedBy { it.ordinal }.joinToString(",") { it.token }
        ext[MediaItemExt.CONTROLS] = ItemExtValue.Text(controls)
        val packageName = (target as ItemTarget.App).packageName
        return copy(
            title = title ?: session.title?.takeIf(String::isNotBlank),
            subtitle = subtitle ?: session.artist?.takeIf(String::isNotBlank),
            image = image ?: MediaItemExt.artwork(packageName).takeIf { session.hasArtwork },
            ext = ext,
        )
    }
}

/**
 * The items of the recent players that have no session: one quiet card each (ADR 0003, Amendment 4). They are media
 * items like the others: the same source, the app as target, the hide rules and content levels apply to them, and the
 * title and artist are only what this process last saw (null otherwise). Transient: built every time, never stored.
 */
object RecentPlayerItems {
    fun build(
        sources: List<RecentPlayerSource>,
        appLabel: (String) -> String?,
        canResume: (String) -> Boolean,
    ): List<Item> =
        sources.map { source ->
            val player = source.player
            val profile = player.profileId ?: DEFAULT_PROFILE
            val packageId = AppPackageName(player.packageName)
            val ext = LinkedHashMap<ItemExtKey, ItemExtValue>()
            ext[MediaItemExt.SOURCE] = ItemExtValue.Flag(true)
            if (canResume(player.packageName)) ext[MediaItemExt.RESUMABLE] = ItemExtValue.Flag(true)
            Item(
                id = ItemId("${SourceIds.MEDIA.value}:recent:${player.packageName}:$profile"),
                sourceId = SourceIds.MEDIA,
                target = ItemTarget.App(player.packageName, profile),
                title = source.title?.takeIf(String::isNotBlank),
                subtitle = source.artist?.takeIf(String::isNotBlank),
                icon = ItemImageKeys.packageIcon(packageId, AppProfileId(profile)),
                timeEpochMillis = player.lastPlayedEpochMillis.coerceAtLeast(0L),
                groupKey = "${player.packageName}:$profile",
                groupLabel = appLabel(player.packageName) ?: player.packageName,
                actions = listOf(ItemAction.Open(), ItemAction.Dismiss()),
                ext = ext,
            )
        }

    private const val DEFAULT_PROFILE = "personal"
}

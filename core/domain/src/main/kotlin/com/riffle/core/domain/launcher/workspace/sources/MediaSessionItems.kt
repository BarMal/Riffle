package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget

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

/** One upcoming track of a session's queue. Text is user content: transient, never persisted or logged. */
data class MediaSessionQueueEntry(
    val title: String?,
    val artist: String? = null,
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

/** Ext keys the media source adds to its items; the Stream reads them back into `MediaContent`. */
object MediaItemExt {
    val PLAYING = ItemExtKey("media.playing")
    val POSITION = ItemExtKey("media.position")
    val DURATION = ItemExtKey("media.duration")
    val CONTROLS = ItemExtKey("media.controls")
    val QUEUE_SIZE = ItemExtKey("media.queue_size")

    fun queueTitle(index: Int) = ItemExtKey("media.q${index}_title")

    fun queueArtist(index: Int) = ItemExtKey("media.q${index}_artist")

    /** The image handle of a session's artwork; the platform keeps the bitmap in memory only. */
    fun artwork(packageName: String): ItemImageHandle = ItemImageHandle("media-art:$packageName")

    const val MAX_QUEUE = 10
}

/**
 * Adds live session data (play state, position, duration, transport controls, queue, artwork) to the media items the
 * notification source already produced. It only enriches: a session whose package has no visible media item is
 * ignored, so the hide rules, profile visibility and the stale filter that built the items still decide what exists,
 * and a [ItemPrivacy.SENSITIVE] item is never enriched. Existing text is kept; a session only fills a blank.
 */
object MediaSessionItems {
    fun enrich(
        items: List<Item>,
        sessions: List<MediaSessionInfo>,
    ): List<Item> {
        if (sessions.isEmpty()) return items
        val byPackage = sessions.distinctBy { it.packageName }.associateBy { it.packageName }
        return items.map { item ->
            val session = (item.target as? ItemTarget.App)?.let { byPackage[it.packageName] }
            if (session == null || item.privacy == ItemPrivacy.SENSITIVE) item else item.enrichedWith(session)
        }
    }

    private fun Item.enrichedWith(session: MediaSessionInfo): Item {
        val ext = LinkedHashMap(ext)
        session.isPlaying?.let { ext[MediaItemExt.PLAYING] = ItemExtValue.Flag(it) }
        session.positionMillis?.takeIf { it >= 0L }?.let { ext[MediaItemExt.POSITION] = ItemExtValue.Number(it) }
        session.durationMillis?.takeIf { it > 0L }?.let { ext[MediaItemExt.DURATION] = ItemExtValue.Number(it) }
        val controls = session.controls.sortedBy { it.ordinal }.joinToString(",") { it.token }
        ext[MediaItemExt.CONTROLS] = ItemExtValue.Text(controls)
        val queue = session.queue.filter { !it.title.isNullOrBlank() }.take(MediaItemExt.MAX_QUEUE)
        if (queue.isNotEmpty()) {
            ext[MediaItemExt.QUEUE_SIZE] = ItemExtValue.Number(queue.size.toLong())
            queue.forEachIndexed { index, entry ->
                ext[MediaItemExt.queueTitle(index)] = ItemExtValue.Text(entry.title.orEmpty())
                entry.artist?.takeIf(
                    String::isNotBlank,
                )?.let { ext[MediaItemExt.queueArtist(index)] = ItemExtValue.Text(it) }
            }
        }
        val packageName = (target as ItemTarget.App).packageName
        return copy(
            title = title ?: session.title?.takeIf(String::isNotBlank),
            subtitle = subtitle ?: session.artist?.takeIf(String::isNotBlank),
            image = image ?: MediaItemExt.artwork(packageName).takeIf { session.hasArtwork },
            ext = ext,
        )
    }
}

package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper

/** Reads the detail the media and notification sources add to an [Item]'s ext back into typed values. */
internal object StreamContentDetails {
    fun queue(item: Item): List<MediaQueueEntry> {
        val size = (item.number(MediaItemExt.QUEUE_SIZE) ?: 0L).toInt()
        return (0 until size.coerceAtMost(MediaItemExt.MAX_QUEUE)).mapNotNull { index ->
            val title = item.text(MediaItemExt.queueTitle(index)) ?: return@mapNotNull null
            MediaQueueEntry(title, item.text(MediaItemExt.queueArtist(index)))
        }
    }

    fun thread(item: Item): List<NotificationMessage> {
        val size = (item.number(NotificationItemMapper.THREAD_SIZE_KEY) ?: 0L).toInt()
        return (0 until size.coerceAtMost(NotificationItemMapper.MAX_THREAD)).mapNotNull { index ->
            val text = item.text(ItemExtKey("notification.m${index}_text")) ?: return@mapNotNull null
            NotificationMessage(
                sender = item.text(ItemExtKey("notification.m${index}_sender"))?.takeIf(String::isNotBlank),
                text = text,
                timestampEpochMillis = item.number(ItemExtKey("notification.m${index}_time")) ?: 0L,
            )
        }
    }

    private fun Item.text(key: ItemExtKey): String? = (ext[key] as? ItemExtValue.Text)?.value

    private fun Item.number(key: ItemExtKey): Long? = (ext[key] as? ItemExtValue.Number)?.value
}

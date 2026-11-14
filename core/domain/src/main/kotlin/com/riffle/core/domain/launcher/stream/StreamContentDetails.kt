package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
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

    /** Whether the notification is a conversation with a person (a messaging style, or the message category). */
    fun isConversation(item: Item): Boolean =
        item.ext[NotificationItemMapper.CONVERSATION_KEY] == ItemExtValue.Flag(true)

    /** What a notification offers: its own actions, reply and snooze where the platform allows them, and Hide. */
    fun notificationActions(item: Item): Set<NotificationAction> =
        buildSet {
            item.actions.forEach { action ->
                when (action) {
                    is ItemAction.Open -> add(NotificationAction.OPEN)
                    is ItemAction.Dismiss -> add(NotificationAction.DISMISS)
                    is ItemAction.Reply -> add(NotificationAction.REPLY)
                    is ItemAction.Snooze -> add(NotificationAction.SNOOZE)
                    is ItemAction.Custom -> Unit
                }
            }
            if (item.ext[NotificationItemMapper.REPLY_KEY] == ItemExtValue.Flag(true)) add(NotificationAction.REPLY)
            if (item.ext[NotificationItemMapper.SNOOZE_KEY] == ItemExtValue.Flag(true)) add(NotificationAction.SNOOZE)
            add(NotificationAction.HIDE)
        }

    /** The image handle an ext entry of [item] names (a contact photo, a picture), or null. */
    fun imageHandle(
        item: Item,
        key: ItemExtKey,
    ): ItemImageHandle? = item.text(key)?.takeIf(String::isNotBlank)?.let(::ItemImageHandle)

    private fun Item.text(key: ItemExtKey): String? = (ext[key] as? ItemExtValue.Text)?.value

    private fun Item.number(key: ItemExtKey): Long? = (ext[key] as? ItemExtValue.Number)?.value
}

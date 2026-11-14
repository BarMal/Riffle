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

    /** The notification's own action buttons the mapper noted on [item] (ids and titles), in order. */
    fun ownActions(item: Item): List<NotificationOwnAction> {
        val count = (item.number(NotificationItemMapper.OWN_COUNT_KEY) ?: 0L).toInt()
        return (0 until count.coerceAtMost(NotificationOwnActions.MAX)).mapNotNull { index ->
            val id = item.text(ItemExtKey("notification.own${index}_id")) ?: return@mapNotNull null
            val title = item.text(ItemExtKey("notification.own${index}_title")) ?: return@mapNotNull null
            NotificationOwnAction(id, title)
        }
    }

    /**
     * What was read out of the notification's own layout, as the mapper noted it on [item]; null when it noted none.
     * The lines and the embedded layout are body text and the picture is an image: [showBody] and [showImages] say
     * which the content level lets through (the title follows the title, which the caller has already redacted).
     */
    fun custom(
        item: Item,
        showBody: Boolean,
        showImages: Boolean,
    ): CustomNotificationContent? {
        if (item.ext[NotificationItemMapper.CUSTOM_KEY] != ItemExtValue.Flag(true)) return null

        fun lines(
            count: ItemExtKey,
            prefix: String,
        ): List<String> =
            (0 until (item.number(count) ?: 0L).toInt().coerceAtMost(CustomNotificationMapping.MAX_BIG_LINES))
                .mapNotNull { index -> item.text(ItemExtKey("$prefix$index")) }
        return CustomNotificationContent(
            title = item.text(NotificationItemMapper.CUSTOM_TITLE_KEY),
            lines =
                if (showBody) {
                    lines(
                        NotificationItemMapper.CUSTOM_LINES_KEY,
                        "notification.custom_l",
                    )
                } else {
                    emptyList()
                },
            bigLines =
                if (showBody) {
                    lines(
                        NotificationItemMapper.CUSTOM_BIG_LINES_KEY,
                        "notification.custom_b",
                    )
                } else {
                    emptyList()
                },
            thumbnail = if (showImages) imageHandle(item, NotificationItemMapper.CUSTOM_THUMBNAIL_KEY) else null,
            embed = showBody && item.ext[NotificationItemMapper.CUSTOM_EMBED_KEY] == ItemExtValue.Flag(true),
            revision = item.number(NotificationItemMapper.CUSTOM_REVISION_KEY) ?: 0L,
        )
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

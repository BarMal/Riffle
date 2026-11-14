package com.riffle.app.launcher.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.stream.CustomNotificationContent
import com.riffle.core.domain.launcher.stream.CustomNotificationMapping
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * What the Stream knows of the notifications that draw themselves with a layout of their own (custom views) and fill
 * in no title or text: the layout's texts and its largest picture, read by inflating the layout off-screen
 * ([RemoteViewsReader]), and the layouts themselves for a card that has to embed one. Memory only, like
 * [NotificationImageStore]: nothing here is written, logged or put in diagnostics, and an entry goes when its
 * notification does (and all of them when the listener disconnects).
 *
 * The listener only hands a notification over ([post], any thread, never blocking): the layout is inflated later, on
 * the main thread, at most once per [MIN_INTERVAL_MILLIS] for one notification, so a navigation notification that
 * updates many times a second does not thrash. Observers are told when what a card would draw has changed.
 */
@Suppress("TooManyFunctions")
internal object NotificationCustomContentStore {
    /** The least time between two reads of one notification's layout: at most two a second. */
    const val MIN_INTERVAL_MILLIS = 500L

    const val IMAGE_PREFIX = "notification-custom:"

    private class Entry(
        val profileId: AppProfileId,
        @Volatile var compact: RemoteViews?,
        @Volatile var big: RemoteViews?,
    ) {
        @Volatile var content: CustomNotificationContent? = null

        @Volatile var picture: Bitmap? = null

        @Volatile var revision: Long = 0L

        /** When the layout was last read, on the scheduler's clock; null before the first read. */
        var lastReadAt: Long? = null
        var pending: Runnable? = null
    }

    /** Runs the reads: the main thread's handler, replaced by a test with its own clock and queue. */
    internal interface Scheduler {
        fun now(): Long

        fun postDelayed(
            delayMillis: Long,
            task: Runnable,
        )

        fun cancel(task: Runnable)
    }

    private class MainScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun now(): Long = SystemClock.uptimeMillis()

        override fun postDelayed(
            delayMillis: Long,
            task: Runnable,
        ) {
            handler.postDelayed(task, delayMillis)
        }

        override fun cancel(task: Runnable) = handler.removeCallbacks(task)
    }

    @Volatile internal var scheduler: Scheduler = MainScheduler()

    /** How a layout is read; a test replaces it to count reads or to fail. */
    @Volatile internal var reader: (Context, RemoteViews) -> ReadLayout? = RemoteViewsReader::read

    private val entries = ConcurrentHashMap<String, Entry>()
    private val observers = CopyOnWriteArraySet<() -> Unit>()

    /** Tells [onChanged] when a card's custom content has changed; the returned function stops it. */
    fun observe(onChanged: () -> Unit): () -> Unit {
        observers += onChanged
        return { observers -= onChanged }
    }

    /**
     * A notification was posted or posted again. One with a layout of its own and no title or text is read (again); any
     * other has no entry. Returns at once: the read is scheduled.
     */
    fun post(
        context: Context,
        notification: StatusBarNotification,
        profileId: AppProfileId,
    ) {
        val key = notification.key
        val platform = notification.notification
        val compact = platform.customContent()
        val big = platform.customBigContent()
        if ((compact == null && big == null) || platform.hasStandardText()) {
            remove(key)
            return
        }
        val entry =
            entries.compute(key) { _, existing ->
                existing?.also {
                    it.compact = compact
                    it.big = big
                } ?: Entry(profileId, compact, big)
            } ?: return
        schedule(context.applicationContext, key, entry)
    }

    private fun schedule(
        context: Context,
        key: String,
        entry: Entry,
    ) {
        synchronized(entry) {
            // One read is already waiting: it will read the newest layout.
            if (entry.pending != null) return
            val last = entry.lastReadAt
            val delay = if (last == null) 0L else (last + MIN_INTERVAL_MILLIS - scheduler.now()).coerceAtLeast(0L)
            val task = Runnable { read(context, key, entry) }
            entry.pending = task
            scheduler.postDelayed(delay, task)
        }
    }

    private fun read(
        context: Context,
        key: String,
        entry: Entry,
    ) {
        synchronized(entry) {
            entry.pending = null
            entry.lastReadAt = scheduler.now()
        }
        // The notification went while the read waited: nothing to read, nothing kept.
        if (entries[key] !== entry) return
        val (next, picture) = extract(context, key, entry)
        val previous = entry.content
        entry.picture = picture
        // The same words as before: the card has nothing to redraw (an embedded layout is applied again, though: what
        // changed in it cannot be seen from here).
        val same = previous != null && !next.embed && previous == next.copy(revision = previous.revision)
        if (!same) {
            entry.revision = next.revision
            entry.content = next
            if (entries[key] === entry) observers.forEach { runCatching(it) }
        }
    }

    private fun extract(
        context: Context,
        key: String,
        entry: Entry,
    ): Pair<CustomNotificationContent, Bitmap?> {
        val compact = entry.compact?.let { safeRead(context, it) }
        val big = entry.big?.let { safeRead(context, it) }
        val compactText = CustomNotificationMapping.map(compact?.texts.orEmpty(), CustomNotificationMapping.MAX_LINES)
        val bigText = CustomNotificationMapping.map((big ?: compact)?.texts.orEmpty())
        val picture = compact?.picture ?: big?.picture
        val inflated = compact != null || big != null
        val content =
            CustomNotificationContent(
                title = compactText.title ?: bigText.title,
                lines = compactText.lines,
                bigLines = bigText.lines,
                thumbnail = picture?.let { ItemImageKeys.notificationCustomImage(entry.profileId, key) },
                // Nothing to draw our way, and a layout that does inflate: the card shows the app's own.
                embed = inflated && compactText.isEmpty && bigText.isEmpty,
                revision = entry.revision + 1,
            )
        return content to picture
    }

    private fun safeRead(
        context: Context,
        views: RemoteViews,
    ): ReadLayout? = runCatching { reader(context, views) }.getOrNull()

    /** What a card draws for the notification [key]; null when it has no layout of its own or was not read yet. */
    fun contentFor(key: LauncherNotificationKey): CustomNotificationContent? = entries[key.value]?.content

    /** The layout a card embeds for [key]: the expanded one when [big] (else, and without one, the compact one). */
    fun viewsFor(
        key: String,
        big: Boolean,
    ): RemoteViews? = entries[key]?.let { entry -> if (big) entry.big ?: entry.compact else entry.compact ?: entry.big }

    fun holds(handleKey: String): Boolean = handleKey.startsWith(IMAGE_PREFIX)

    /** The picture read out of the layout of the notification this handle names. */
    fun bitmapFor(handleKey: String): Bitmap? =
        entries.entries.firstOrNull { (key, entry) ->
            ItemImageKeys.notificationCustomImage(entry.profileId, key).key == handleKey
        }?.value?.picture

    /** The notification [key] was removed: its content, picture and layouts are dropped, and a waiting read with it. */
    fun remove(key: String) {
        val entry = entries.remove(key) ?: return
        synchronized(entry) {
            entry.pending?.let(scheduler::cancel)
            entry.pending = null
        }
        if (entry.content != null) observers.forEach { runCatching(it) }
    }

    /** Only the notifications in [keys] are still posted. */
    fun retain(keys: Set<String>) {
        entries.keys.filter { it !in keys }.forEach(::remove)
    }

    fun clear() {
        entries.keys.toList().forEach(::remove)
    }
}

/** The layout the app set for the compact form; null when the notification uses a standard template. */
@Suppress("DEPRECATION")
internal fun Notification.customContent(): RemoteViews? = runCatching { contentView }.getOrNull()

@Suppress("DEPRECATION")
internal fun Notification.customBigContent(): RemoteViews? = runCatching { bigContentView }.getOrNull()

/** Whether the app filled in a title or a text: then the standard fields are what the card draws. */
private fun Notification.hasStandardText(): Boolean =
    sequenceOf(
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TITLE_BIG,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
    ).any { key -> runCatching { extras?.getCharSequence(key)?.isNotBlank() == true }.getOrDefault(false) }

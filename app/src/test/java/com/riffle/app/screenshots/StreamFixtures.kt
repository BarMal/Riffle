package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.expressions.FakeExpressionImageLoader
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamFormats
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.ArticleAction
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventAction
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.MediaQueueEntry
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.NotificationMessage
import com.riffle.core.domain.launcher.stream.RecentAppAction
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A clock and a locale that never move, so the Stream's text is the same on every run. */
internal class FixedStreamFormats(private val nowEpochMillis: Long = StreamFixtures.NOW) : StreamFormats {
    override val live: Boolean = false

    override fun now(): Long = nowEpochMillis

    override fun age(epochMillis: Long): String = "${(nowEpochMillis - epochMillis) / MINUTE} min. ago"

    override fun time(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(epochMillis))

    override fun dayLabel(day: LocalDate): String = day.toString()

    override fun today(): LocalDate = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()

    override fun zone(): java.time.ZoneId = ZoneOffset.UTC

    private companion object {
        const val MINUTE = 60_000L
    }
}

/**
 * Images for the Stream's screenshots: a picture-like scene for a notification's picture and for artwork, a face-like
 * disc for a contact photo, and the plain coloured discs of [FakeExpressionImageLoader] for everything else. Nothing is
 * read from disk or the network.
 */
internal class FixtureImageLoader : ExpressionImageLoader {
    private val plain = FakeExpressionImageLoader()

    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap? =
        when {
            handle.key.startsWith("notification-picture:") -> scene(sizePx, widthOverHeight = 1.5f)
            handle.key.startsWith("media-art:") -> scene(sizePx, widthOverHeight = 1f)
            handle.key.startsWith("notification-person:") -> face(sizePx)
            else -> plain.load(handle, sizePx)
        }

    private fun scene(
        sizePx: Int,
        widthOverHeight: Float,
    ): ImageBitmap {
        val width = sizePx.coerceAtLeast(2)
        val height = (width / widthOverHeight).toInt().coerceAtLeast(2)
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)

        fun fill(
            color: Long,
            draw: (Paint) -> Unit,
        ) = draw(Paint().apply { this.color = Color(color) })
        fill(0xFF7CC3F2) { canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), it) }
        fill(0xFFFFD54F) { canvas.drawCircle(Offset(width * 0.75f, height * 0.28f), height * 0.14f, it) }
        fill(0xFF3E8E55) { canvas.drawCircle(Offset(width * 0.3f, height * 1.15f), height * 0.7f, it) }
        fill(0xFF2F6F45) { canvas.drawCircle(Offset(width * 0.85f, height * 1.25f), height * 0.6f, it) }
        return bitmap
    }

    private fun face(sizePx: Int): ImageBitmap {
        val side = sizePx.coerceAtLeast(2)
        val bitmap = ImageBitmap(side, side)
        val canvas = Canvas(bitmap)
        val half = side / 2f
        canvas.drawRect(Rect(0f, 0f, side.toFloat(), side.toFloat()), Paint().apply { color = Color(0xFF6C7BD9) })
        canvas.drawCircle(Offset(half, side * 0.4f), side * 0.2f, Paint().apply { color = Color(0xFFF2C6A0) })
        canvas.drawCircle(Offset(half, side * 1.0f), side * 0.38f, Paint().apply { color = Color(0xFFF2C6A0) })
        return bitmap
    }
}

/** One of every typed content, for the Stream's screenshots and tests. Nothing reads a clock or the network. */
internal object StreamFixtures {
    const val NOW = ScreenshotFixtures.FIXED_NOW_EPOCH_MILLIS
    private const val MINUTE = 60_000L

    private fun ref(app: com.riffle.core.domain.launcher.apps.InstalledApp) =
        StreamAppRef(app.identity.packageName.value, app.identity.profile.id.value)

    private val chat = ScreenshotFixtures.chat
    private val mail = ScreenshotFixtures.mail
    private val music = ScreenshotFixtures.music
    private val icon = ItemImageHandle("icon")

    val media =
        MediaContent(
            id = StreamContentId("media:music"),
            app = ref(music),
            appLabel = "Music",
            title = "Midnight City",
            artist = "M83",
            artwork = ItemImageHandle("media-art:music"),
            icon = icon,
            isPlaying = true,
            positionMillis = 83_000L,
            durationMillis = 244_000L,
            queue =
                listOf(
                    MediaQueueEntry("Reunion", "M83"),
                    MediaQueueEntry("Wait", "M83"),
                    MediaQueueEntry("Steve McQueen", "M83"),
                ),
            target = ItemTarget.App(music.identity.packageName.value, "personal"),
            actions = MediaAction.entries.toSet(),
            postedAtEpochMillis = NOW - MINUTE,
            privacy = com.riffle.core.domain.launcher.stream.StreamPrivacy.VISIBLE,
        )

    val nextEvent =
        EventContent(
            id = StreamContentId("calendar:standup"),
            kind = EventKind.CALENDAR,
            title = "Design standup",
            location = "Room 4",
            calendarName = null,
            startEpochMillis = NOW + 12 * MINUTE,
            endEpochMillis = NOW + 42 * MINUTE,
            allDay = false,
            recurring = true,
            target = ItemTarget.DeepLink("content://events/1"),
            actions = setOf(EventAction.OPEN, EventAction.EXPAND),
            privacy = com.riffle.core.domain.launcher.stream.StreamPrivacy.VISIBLE,
        )

    val feedEvent =
        nextEvent.copy(
            id = StreamContentId("ics:launch"),
            kind = EventKind.CALENDAR_FEED,
            title = "Launch review",
            location = null,
            calendarName = "Product calendar",
            startEpochMillis = NOW + 26 * 60 * MINUTE,
            endEpochMillis = NOW + 27 * 60 * MINUTE,
            recurring = false,
        )

    /** A second event today, after the standup. */
    val lunchEvent =
        nextEvent.copy(
            id = StreamContentId("calendar:lunch"),
            title = "Lunch with Sam",
            location = "Corner cafe",
            startEpochMillis = NOW + 4 * 60 * MINUTE,
            endEpochMillis = NOW + 5 * 60 * MINUTE,
            recurring = false,
        )

    val alarm =
        EventContent(
            id = StreamContentId("alarm:next"),
            kind = EventKind.ALARM,
            title = null,
            location = null,
            calendarName = null,
            startEpochMillis = NOW + 9 * 60 * MINUTE,
            endEpochMillis = null,
            allDay = false,
            recurring = false,
            target = ItemTarget.Intent("alarm.open_clock"),
            actions = setOf(EventAction.OPEN),
            privacy = com.riffle.core.domain.launcher.stream.StreamPrivacy.VISIBLE,
        )

    val chatNotification =
        NotificationContent(
            id = StreamContentId("notifications:chat:1"),
            app = ref(chat),
            appLabel = "Chat",
            title = "Sam",
            text = "Are we still on for lunch tomorrow?",
            icon = icon,
            image = null,
            postedAtEpochMillis = NOW - 4 * MINUTE,
            category = null,
            priority = NotificationPriority.DEFAULT,
            target = ItemTarget.App(chat.identity.packageName.value, "personal"),
            actions = NotificationAction.entries.toSet(),
            privacy = com.riffle.core.domain.launcher.stream.StreamPrivacy.VISIBLE,
            notificationKey = "0|chat|1",
            person = ItemImageHandle("notification-person:personal:0|chat|1"),
            picture = ItemImageHandle("notification-picture:personal:0|chat|1"),
            thread =
                listOf(
                    NotificationMessage("Sam", "Morning! Got a minute?", NOW - 20 * MINUTE),
                    NotificationMessage(null, "Sure, what's up?", NOW - 12 * MINUTE),
                    NotificationMessage("Sam", "Are we still on for lunch tomorrow?", NOW - 4 * MINUTE),
                ),
        )

    val chatNotificationTwo =
        chatNotification.copy(
            id = StreamContentId("notifications:chat:2"),
            title = "Priya",
            text = "Photos from the weekend are up.",
            postedAtEpochMillis = NOW - 30 * MINUTE,
            notificationKey = "0|chat|2",
            thread = emptyList(),
            person = null,
            picture = null,
        )

    val mailNotification =
        chatNotification.copy(
            id = StreamContentId("notifications:mail:1"),
            app = ref(mail),
            appLabel = "Mail",
            title = "Invoice 2026-114",
            text = "Your invoice is ready to download.",
            postedAtEpochMillis = NOW - 55 * MINUTE,
            notificationKey = "0|mail|1",
            actions = setOf(NotificationAction.OPEN, NotificationAction.DISMISS, NotificationAction.HIDE),
            thread = emptyList(),
            person = null,
            picture = null,
        )

    val recents: List<RecentAppContent> =
        listOf(ScreenshotFixtures.chat, ScreenshotFixtures.mail, ScreenshotFixtures.maps, ScreenshotFixtures.photos)
            .mapIndexed { index, app ->
                RecentAppContent(
                    id = StreamContentId("recent:${app.identity.packageName.value}"),
                    app = ref(app),
                    label = app.label,
                    icon = icon,
                    lastUsedEpochMillis = NOW - (index + 1) * 7 * MINUTE,
                    target = ItemTarget.App(app.identity.packageName.value, "personal"),
                    actions = RecentAppAction.entries.toSet(),
                )
            }

    val frequent: List<RecentAppContent> =
        listOf(ScreenshotFixtures.browser, ScreenshotFixtures.notes).map { app ->
            recents.first().copy(
                id = StreamContentId("recent:${app.identity.packageName.value}"),
                app = ref(app),
                label = app.label,
                lastUsedEpochMillis = NOW - 3 * 60 * MINUTE,
                target = ItemTarget.App(app.identity.packageName.value, "personal"),
                foregroundMillis = 3_600_000L,
            )
        }

    val articles: List<ArticleContent> =
        listOf(
            article("a1", "A new look for the launcher: glass, everywhere", "Riffle blog", 35, withImage = true),
            article("a2", "How the card stack decides where to settle", "Riffle blog", 180, withImage = false),
        )

    val articlesOtherFeed =
        article("b1", "Notes from the desktop shell", "Serein notes", 240, withImage = true, feed = "feed-b")

    private fun article(
        id: String,
        title: String,
        feedName: String,
        minutesAgo: Long,
        withImage: Boolean,
        feed: String = "feed-a",
    ) = ArticleContent(
        id = StreamContentId("rss:$id"),
        feedId = feed,
        feedName = feedName,
        title = title,
        summary = null,
        author = null,
        image = if (withImage) ItemImageHandle("feed-art:$id") else null,
        publishedAtEpochMillis = NOW - minutesAgo * MINUTE,
        stale = false,
        target = ItemTarget.DeepLink("https://example.invalid/$id"),
        actions = setOf(ArticleAction.OPEN, ArticleAction.MARK_READ),
        privacy = com.riffle.core.domain.launcher.stream.StreamPrivacy.VISIBLE,
        digest = "digest-$id",
    )

    val tasks =
        listOf(
            TaskContent(
                StreamContentId("todoist:1"),
                "Renew passport",
                listName = "Errands",
                dueEpochMillis = NOW - 26 * 60 * MINUTE,
                overdue = true,
                priority = 4,
            ),
            TaskContent(
                StreamContentId("todoist:2"),
                "Send the invoice",
                dueEpochMillis = NOW + 5 * 60 * MINUTE,
                dueHasTime = true,
            ),
            TaskContent(StreamContentId("todoist:3"), "Water the plants", dueEpochMillis = NOW),
        )

    val staleTasks = tasks.map { it.copy(stale = true) }

    /** Three shortcuts of [app], as the launcher reads them (one with a long label, one disabled). */
    fun shortcutsOf(
        app: com.riffle.core.domain.launcher.apps.InstalledApp,
    ): com.riffle.core.domain.launcher.apps.AppShortcutsByApp =
        mapOf(
            app.identity to
                listOf(
                    com.riffle.core.domain.launcher.apps.AppShortcut(
                        com.riffle.core.domain.launcher.apps.AppShortcutId("recent"),
                        app.identity,
                        "Recent",
                        "Recent files",
                        rank = 0,
                    ),
                    com.riffle.core.domain.launcher.apps.AppShortcut(
                        com.riffle.core.domain.launcher.apps.AppShortcutId("search"),
                        app.identity,
                        "Search",
                        null,
                        rank = 1,
                    ),
                    com.riffle.core.domain.launcher.apps.AppShortcut(
                        com.riffle.core.domain.launcher.apps.AppShortcutId("scan"),
                        app.identity,
                        "Scan document",
                        "Scan a document",
                        enabled = false,
                        disabledMessage = "Camera access is off",
                        rank = 2,
                    ),
                ),
        )

    private fun group(
        key: String,
        label: String?,
        items: List<StreamContent>,
        total: Int = items.size,
    ) = StreamGroup(key, label, items, total)

    /** A section with content, as the provider would deliver it. */
    fun ready(section: StreamSection): StreamSectionResult.Ready =
        StreamSectionResult.Ready(
            when (section) {
                StreamSection.NOW -> listOf(group(StreamGroupKeys.NOW, null, listOf(media, nextEvent, alarm)))
                StreamSection.NOTIFICATIONS ->
                    listOf(
                        group("chat:personal", "Chat", listOf(chatNotification, chatNotificationTwo), total = 5),
                        group("mail:personal", "Mail", listOf(mailNotification)),
                    )
                StreamSection.RECENTS ->
                    listOf(
                        group(StreamGroupKeys.RECENTS, null, recents),
                        group(StreamGroupKeys.FREQUENT, null, frequent),
                    )
                StreamSection.TODAY ->
                    listOf(
                        group("2026-01-01", null, listOf(nextEvent, lunchEvent)),
                        group("2026-01-02", null, listOf(feedEvent)),
                        group(StreamGroupKeys.TASKS, null, tasks),
                    )
                StreamSection.SHORTCUTS -> emptyList()
                StreamSection.FEEDS ->
                    listOf(
                        group("feed-a", "Riffle blog", articles),
                        group("feed-b", "Serein notes", listOf(articlesOtherFeed)),
                    )
            },
        )

    /** The content a glance widget of [kind] shows. */
    fun contentFor(kind: com.riffle.core.domain.launcher.glance.GlanceWidgetKind): StreamContent =
        when (kind) {
            com.riffle.core.domain.launcher.glance.GlanceWidgetKind.NOW_PLAYING -> media
            com.riffle.core.domain.launcher.glance.GlanceWidgetKind.NEXT_EVENT -> nextEvent
            com.riffle.core.domain.launcher.glance.GlanceWidgetKind.TOP_STORY -> articles.first()
        }

    /** The same content as a source would deliver it under a Hide or content-level rule: text withheld. */
    fun redactedFor(kind: com.riffle.core.domain.launcher.glance.GlanceWidgetKind): StreamContent =
        when (val content = contentFor(kind)) {
            is MediaContent ->
                content.copy(title = null, artist = null, artwork = null, privacy = StreamPrivacy.REDACTED)
            is EventContent ->
                content.copy(title = null, location = null, endEpochMillis = null, privacy = StreamPrivacy.REDACTED)
            is ArticleContent ->
                content.copy(title = null, image = null, digest = null, privacy = StreamPrivacy.REDACTED)
            else -> content
        }

    fun environment(reducedMotion: Boolean = false) =
        StreamEnvironment(
            imageLoader = FixtureImageLoader(),
            appIconLoader = SolidColorAppIconLoader(),
            installedApps = ScreenshotFixtures.installedApps,
            reducedMotion = reducedMotion,
            formats = FixedStreamFormats(),
            nowEpochMillis = NOW,
        )

    /** Callbacks that record nothing; the behaviour tests build their own. */
    fun callbacks() = StreamCallbacks()
}

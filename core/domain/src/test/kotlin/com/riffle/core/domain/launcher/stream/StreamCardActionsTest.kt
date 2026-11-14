package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.workspace.ItemTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamCardActionsTest {
    private val chat =
        NotificationContent(
            id = StreamContentId("n1"),
            app = StreamAppRef("chat", "personal"),
            appLabel = "Chat",
            title = "Sam",
            text = "Lunch?",
            icon = null,
            image = null,
            postedAtEpochMillis = 1L,
            category = null,
            priority = null,
            target = ItemTarget.App("chat", "personal"),
            actions =
                setOf(
                    NotificationAction.OPEN,
                    NotificationAction.DISMISS,
                    NotificationAction.REPLY,
                    NotificationAction.SNOOZE,
                    NotificationAction.HIDE,
                ),
            privacy = StreamPrivacy.VISIBLE,
            notificationKey = "k1",
        )
    private val player =
        MediaContent(
            id = StreamContentId("m1"),
            app = StreamAppRef("music", "personal"),
            appLabel = "Music",
            title = "Song",
            artist = "Artist",
            artwork = null,
            icon = null,
            isPlaying = true,
            queue = listOf(MediaQueueEntry("Next song")),
            target = ItemTarget.App("music", "personal"),
            actions = MediaAction.entries.toSet(),
            privacy = StreamPrivacy.VISIBLE,
        )
    private val event =
        EventContent(
            id = StreamContentId("e1"),
            kind = EventKind.CALENDAR,
            title = "Standup",
            location = null,
            calendarName = null,
            startEpochMillis = 10L,
            endEpochMillis = 20L,
            allDay = false,
            recurring = false,
            target = ItemTarget.DeepLink("content://events/1"),
            actions = setOf(EventAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
        )
    private val task = TaskContent(id = StreamContentId("t1"), title = "Send the invoice")
    private val article =
        ArticleContent(
            id = StreamContentId("a1"),
            feedId = "f",
            feedName = "Feed",
            title = "Headline",
            summary = "Summary",
            author = null,
            image = null,
            publishedAtEpochMillis = 1L,
            stale = false,
            target = ItemTarget.DeepLink("https://example.org/a"),
            actions = setOf(ArticleAction.OPEN, ArticleAction.MARK_READ),
            privacy = StreamPrivacy.VISIBLE,
            digest = "d",
        )
    private val shortcut =
        AppShortcutContent(
            id = StreamContentId("s1"),
            app = StreamAppRef("files", "personal"),
            appIdentity =
                com.riffle.core.domain.launcher.apps.AppIdentity(
                    com.riffle.core.domain.launcher.apps.AppPackageName("files"),
                    com.riffle.core.domain.launcher.apps.AppActivityName("Main"),
                    com.riffle.core.domain.launcher.apps.AppProfile.personal(),
                ),
            appLabel = "Files",
            shortcutId = "x",
            shortLabel = "Downloads",
            longLabel = null,
            icon = null,
        )

    @Test
    fun aConversationHasAReplyFieldAndDismissAndSnooze() {
        assertEquals(
            listOf(CardAction.Reply, CardAction.Dismiss, CardAction.Snooze),
            StreamCardActions.forContent(chat),
        )
    }

    @Test
    fun aNotificationOffersOnlyWhatItCanDo() {
        val noReply = chat.copy(actions = chat.actions - NotificationAction.REPLY - NotificationAction.SNOOZE)
        assertEquals(listOf(CardAction.Dismiss), StreamCardActions.forContent(noReply))
        // A redacted one has the notification key withheld: nothing but the card itself.
        assertEquals(
            emptyList(),
            StreamCardActions.forContent(chat.copy(notificationKey = null, privacy = StreamPrivacy.REDACTED)),
        )
    }

    @Test
    fun aPlayerHasTransportAndAQueueEntry() {
        assertEquals(
            listOf(
                CardAction.Previous,
                CardAction.PlayPause(playing = true),
                CardAction.Next,
                CardAction.ToggleQueue(shown = true),
            ),
            StreamCardActions.forContent(player),
        )
        assertEquals(
            CardAction.PlayPause(playing = false),
            StreamCardActions.forContent(
                player.copy(isPlaying = false),
            ).filterIsInstance<CardAction.PlayPause>().single(),
        )
    }

    @Test
    fun aPlayerWithoutAQueueOrASeekableSessionDropsWhatItCannotDo() {
        val bare = player.copy(queue = emptyList(), actions = setOf(MediaAction.PLAY, MediaAction.PAUSE))
        assertEquals(listOf(CardAction.PlayPause(playing = true)), StreamCardActions.forContent(bare))
    }

    @Test
    fun anEventOpensInTheCalendarAndAnAlarmInTheClock() {
        assertEquals(listOf(CardAction.OpenEvent(EventKind.CALENDAR)), StreamCardActions.forContent(event))
        assertEquals(
            listOf(CardAction.OpenEvent(EventKind.ALARM)),
            StreamCardActions.forContent(event.copy(kind = EventKind.ALARM)),
        )
    }

    @Test
    fun aTaskCompletesAndACompletedTaskOffersNothing() {
        assertEquals(listOf(CardAction.Complete), StreamCardActions.forContent(task))
        assertEquals(emptyList(), StreamCardActions.forContent(task.copy(completed = true)))
    }

    @Test
    fun anArticleAShortcutAndARecentAppHaveNothingExtraAndDoNotOpenInPlace() {
        assertEquals(emptyList(), StreamCardActions.forContent(article))
        assertEquals(emptyList(), StreamCardActions.forContent(shortcut))
        assertFalse(StreamCardActions.opensInPlace(shortcut))
        assertFalse(StreamCardActions.opensInPlace(article))
        assertTrue(listOf(chat, player, event, task).all(StreamCardActions::opensInPlace))
    }

    @Test
    fun everyControlMapsToTheActionItPerformsAndTheQueueToggleToNone() {
        assertEquals(StreamAction.Reply("hi"), StreamCardActions.streamActionFor(CardAction.Reply, "hi"))
        assertEquals(StreamAction.Pause, StreamCardActions.streamActionFor(CardAction.PlayPause(playing = true)))
        assertEquals(StreamAction.Play, StreamCardActions.streamActionFor(CardAction.PlayPause(playing = false)))
        assertEquals(StreamAction.Open, StreamCardActions.streamActionFor(CardAction.OpenEvent(EventKind.CALENDAR)))
        assertEquals(StreamAction.Complete, StreamCardActions.streamActionFor(CardAction.Complete))
        assertNull(StreamCardActions.streamActionFor(CardAction.ToggleQueue(shown = true)))
    }

    @Test
    fun theActionsAreInTheDockOnlyWhereTheDockCanHoldARow() {
        val inDock = CardActionsPlacement.IN_DOCK
        assertEquals(inDock, StreamCardActions.effectiveActionsPlacement(inDock, DockPosition.BOTTOM))
        assertEquals(inDock, StreamCardActions.effectiveActionsPlacement(inDock, DockPosition.TOP))
        assertEquals(
            CardActionsPlacement.ON_CARD,
            StreamCardActions.effectiveActionsPlacement(inDock, DockPosition.LEFT),
        )
        assertEquals(
            CardActionsPlacement.ON_CARD,
            StreamCardActions.effectiveActionsPlacement(inDock, DockPosition.RIGHT),
        )
        assertEquals(
            CardActionsPlacement.ON_CARD,
            StreamCardActions.effectiveActionsPlacement(inDock, DockPosition.BOTTOM, hasDock = false),
        )
        assertEquals(
            CardActionsPlacement.ON_CARD,
            StreamCardActions.effectiveActionsPlacement(CardActionsPlacement.ON_CARD, DockPosition.BOTTOM),
        )
    }

    @Test
    fun growsOutOfTheDockIsDrawnAsJustAboveTheDockForNow() {
        assertEquals(
            OpenCardPlacement.ABOVE_DOCK,
            StreamCardActions.effectiveCardPlacement(OpenCardPlacement.GROWS_OUT_OF_DOCK),
        )
        assertEquals(OpenCardPlacement.CENTRE, StreamCardActions.effectiveCardPlacement(OpenCardPlacement.CENTRE))
        assertEquals(
            OpenCardPlacement.ABOVE_DOCK,
            StreamCardActions.effectiveCardPlacement(OpenCardPlacement.ABOVE_DOCK),
        )
    }
}

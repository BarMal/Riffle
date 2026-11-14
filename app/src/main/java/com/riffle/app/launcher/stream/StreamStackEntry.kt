package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamGap
import com.riffle.core.domain.launcher.stream.TodayCard
import com.riffle.core.domain.launcher.stream.TodayNotice

/**
 * One card of a page's stack: a piece of typed content, or one of the cards a page composes around its content (the
 * Today page's checklist and its summary of tomorrow, and the notices that say what a page could not show). The stack
 * only needs a card's identity, its height, what it says (peeking and spoken) and what opening it does, so it draws any
 * of them the same way; each draws itself in [StackEntryCard].
 */
internal sealed interface StackEntry {
    /** Stable across updates: the pager and the stack key their state by it. */
    val id: String

    /** The collapsed height the card is drawn at. */
    val height: Dp

    /** The content the card opens (the tap, the "Open card" action, Enter); null when the card opens nothing. */
    val openContent: StreamContent? get() = null

    /** One line saying what the card is about, for the strip that peeks out. */
    @Composable
    fun peekText(env: StreamEnvironment): String

    /** Everything the card says, for the one node a peeking card is to assistive technology. */
    @Composable
    fun spokenText(env: StreamEnvironment): String
}

/**
 * A typed [content] as its own card. [caption] is said before an event's time (the day, in a pile that spans days, or
 * "Often used" on a recent app); [nextUp] draws an event as the Today page's front card, with a live countdown.
 */
internal data class ContentEntry(
    val content: StreamContent,
    val caption: String? = null,
    val nextUp: Boolean = false,
) : StackEntry {
    override val id: String get() = content.id.value
    override val height: Dp get() = if (nextUp) StreamCardHeights.nextEvent else content.cardHeight()
    override val openContent: StreamContent get() = content

    @Composable
    override fun peekText(env: StreamEnvironment): String = content.peekText()

    @Composable
    override fun spokenText(env: StreamEnvironment): String = content.spokenText(env)
}

/** The Today page's "To do today" checklist. */
internal data class ToDoEntry(val card: TodayCard.ToDo) : StackEntry {
    override val id: String get() = "today:todo"
    override val height: Dp get() = StreamCardHeights.todo

    @Composable
    override fun peekText(env: StreamEnvironment): String = todoCountsText(card)

    @Composable
    override fun spokenText(env: StreamEnvironment): String =
        (
            listOf(stringResource(R.string.stream_today_todo_title), todoCountsText(card)) +
                card.tasks.map {
                    it.title
                }.filterNotNull()
        )
            .joinToString(". ")
}

/** The Today page's summary of tomorrow; a tap opens its first event (else its first task). */
internal data class TomorrowEntry(val card: TodayCard.Tomorrow) : StackEntry {
    override val id: String get() = "today:tomorrow"
    override val height: Dp get() = StreamCardHeights.summary
    override val openContent: StreamContent? get() = card.firstEvent ?: card.firstTask

    @Composable
    override fun peekText(env: StreamEnvironment): String =
        stringResource(R.string.stream_today_tomorrow_title) + ", " + tomorrowCountsText(card)

    @Composable
    override fun spokenText(env: StreamEnvironment): String =
        listOfNotNull(
            stringResource(R.string.stream_today_tomorrow_title),
            tomorrowCountsText(card),
            card.firstEvent?.let { tomorrowFirstText(it, env) },
        ).joinToString(". ")
}

/** What a page could not show and why, with the way out (allow access, connect, retry). [key] tells notices apart. */
internal data class NoticeEntry(val key: String, val notice: Notice) : StackEntry {
    override val id: String get() = "notice:$key"
    override val height: Dp get() = StreamCardHeights.notice

    @Composable
    override fun peekText(env: StreamEnvironment): String = noticeText(notice)

    @Composable
    override fun spokenText(env: StreamEnvironment): String = noticeText(notice)
}

/** What a [NoticeEntry] says. */
internal sealed interface Notice {
    data class Today(val notice: TodayNotice) : Notice

    /** A shortcuts page whose app cannot list its shortcuts because Riffle is not the Home app. */
    data object ShortcutsNeedHome : Notice
}

/** The notices of a page's gaps, as entries after its content. */
internal fun List<StreamGap>.shortcutNotices(): List<StackEntry> =
    mapIndexedNotNull { index, gap ->
        if (gap.source == com.riffle.core.domain.launcher.stream.StreamSourceIds.SHORTCUTS) {
            NoticeEntry("shortcuts:$index", Notice.ShortcutsNeedHome)
        } else {
            null
        }
    }

/** The card for any entry, so a stack draws without knowing what is in it. */
@Composable
@Suppress("LongParameterList")
internal fun StackEntryCard(
    entry: StackEntry,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is ContentEntry ->
            if (entry.nextUp && entry.content is EventContent) {
                NextEventCard(entry.content, env, callbacks, groupKey, modifier)
            } else {
                StreamCard(
                    content = entry.content,
                    env = env,
                    callbacks = callbacks,
                    groupKey = groupKey,
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    modifier = modifier,
                    caption = entry.caption,
                )
            }
        is ToDoEntry -> TodoCard(entry.card, env, callbacks, modifier)
        is TomorrowEntry -> TomorrowCard(entry.card, env, callbacks, modifier)
        is NoticeEntry -> NoticeCard(entry.notice, callbacks, modifier)
    }
}

@Composable
internal fun todoCountsText(card: TodayCard.ToDo): String =
    if (card.overdue > 0) {
        stringResource(R.string.stream_today_todo_counts_overdue, card.left, card.overdue)
    } else {
        stringResource(R.string.stream_today_todo_counts, card.left)
    }

@Composable
internal fun tomorrowCountsText(card: TodayCard.Tomorrow): String =
    listOfNotNull(
        pluralStringResource(R.plurals.stream_today_tomorrow_events, card.eventCount, card.eventCount)
            .takeIf { card.eventCount > 0 },
        pluralStringResource(R.plurals.stream_today_tomorrow_tasks, card.taskCount, card.taskCount)
            .takeIf { card.taskCount > 0 },
    ).joinToString(" · ")

@Composable
internal fun tomorrowFirstText(
    event: EventContent,
    env: StreamEnvironment,
): String =
    if (event.allDay) {
        stringResource(R.string.stream_today_tomorrow_first_all_day, eventTitle(event))
    } else {
        stringResource(
            R.string.stream_today_tomorrow_first,
            eventTitle(event),
            env.formats.time(event.startEpochMillis),
        )
    }

@Composable
internal fun shortcutTitle(content: AppShortcutContent): String =
    if (content.opensApp) {
        stringResource(R.string.stream_shortcut_open_app, content.appLabel ?: content.app.packageName)
    } else {
        content.shortLabel ?: hiddenText()
    }

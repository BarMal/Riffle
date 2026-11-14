@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.NotConnectedReason
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.stream.TodayCard
import com.riffle.core.domain.launcher.stream.TodayCountdown
import com.riffle.core.domain.launcher.stream.TodayNotice
import com.riffle.core.domain.launcher.stream.TodayPlanner
import kotlinx.coroutines.delay

internal const val TODAY_COUNTDOWN_TEST_TAG = "stream-today-countdown"
internal const val TODAY_TODO_TEST_TAG = "stream-today-todo"
internal const val TODAY_TOMORROW_TEST_TAG = "stream-today-tomorrow"
internal const val TODAY_OPEN_IN_CALENDAR_TEST_TAG = "stream-today-open-in-calendar"

private const val MAX_TASKS_ON_TODO_CARD = 3

/** The Today page's cards, front to back, as stack entries (ADR 0003, Amendment 1, "Today"). */
@Composable
internal fun todayEntries(
    body: StreamPageBody.Content,
    env: StreamEnvironment,
): List<StackEntry> {
    val formats = env.formats
    val cards = TodayPlanner.plan(body, env.nowEpochMillis, formats.today(), formats.zone())
    return cards.mapIndexed { index, card ->
        when (card) {
            is TodayCard.NextEvent -> ContentEntry(card.event, nextUp = true)
            is TodayCard.ToDo -> ToDoEntry(card)
            is TodayCard.LaterEvent -> ContentEntry(card.event)
            is TodayCard.Tomorrow -> TomorrowEntry(card)
            is TodayCard.Notice -> NoticeEntry("today:$index", Notice.Today(card.notice))
        }
    }
}

/**
 * The next event as the front card of Today: when it is, its title, place, a countdown that moves with the clock and
 * what the card offers (open it in the calendar). Hide is in the card's menu, as on every event card.
 */
@Composable
internal fun NextEventCard(
    content: EventContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    StreamCardSurface(content.id.value, null, null, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .padding(
                        start = RiffleSpacing.l,
                        end = RiffleSpacing.l,
                        top = StreamHeaderTopPadding,
                        bottom = RiffleSpacing.s,
                    ),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .streamClickable({ callbacks.onAction(content, StreamAction.Open) }),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
            ) {
                StreamCardHeader(
                    icon = { StreamGlyph(StreamIcons.Agenda) },
                    title = eventTitle(content),
                    trailing = { StreamHideMenuButton(hideChoices, content, groupKey, callbacks) },
                )
                Text(
                    text = eventSpanText(content, env),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                (content.location ?: content.calendarName)?.takeIf { it.isNotBlank() }?.let { place ->
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TodayCountdownText(
                    event = content,
                    env = env,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (offersOpen(content)) {
                    StreamTextButton(
                        text = stringResource(R.string.stream_today_open_in_calendar),
                        onClick = { callbacks.onAction(content, StreamAction.Open) },
                        modifier = Modifier.testTag(TODAY_OPEN_IN_CALENDAR_TEST_TAG),
                    )
                }
            }
        }
    }
}

private fun offersOpen(content: EventContent): Boolean =
    com.riffle.core.domain.launcher.stream.StreamActionPolicy.offers(content, StreamAction.Open)

/**
 * The countdown of [event]: "in 25 min", "now", "ends in 10 min". It has its own clock, which wakes only when the
 * words change, so a moving countdown recomposes this text and nothing around it. A fixed clock (screenshots) does not
 * tick.
 */
@Composable
internal fun TodayCountdownText(
    event: EventContent,
    env: StreamEnvironment,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val now = rememberCountdownNow(event, env)
    Text(
        text = todayCountdownWords(TodayCountdown.of(event, now)),
        modifier = modifier.testTag(TODAY_COUNTDOWN_TEST_TAG),
        style = style,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun rememberCountdownNow(
    event: EventContent,
    env: StreamEnvironment,
): Long {
    val formats = env.formats
    var ticking by remember(event, formats) { mutableLongStateOf(env.nowEpochMillis) }
    LaunchedEffect(event, formats) {
        // A fixed clock (screenshots) never ticks.
        while (formats.live) {
            ticking = formats.now()
            delay(TodayCountdown.millisUntilChange(event, ticking))
        }
    }
    return if (formats.live) ticking else env.nowEpochMillis
}

private const val MINUTES_PER_HOUR = 60L

@Composable
internal fun todayCountdownWords(countdown: TodayCountdown): String =
    when (countdown) {
        is TodayCountdown.StartsIn ->
            if (countdown.minutes >= MINUTES_PER_HOUR * 2) {
                val hours = ((countdown.minutes + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR).toInt()
                pluralStringResource(R.plurals.stream_today_countdown_starts_in_hours, hours, hours)
            } else {
                val minutes = countdown.minutes.toInt()
                pluralStringResource(R.plurals.stream_today_countdown_starts_in_minutes, minutes, minutes)
            }
        TodayCountdown.Now -> stringResource(R.string.stream_today_countdown_now)
        is TodayCountdown.EndsIn -> {
            val minutes = countdown.minutes.toInt()
            pluralStringResource(R.plurals.stream_today_countdown_ends_in_minutes, minutes, minutes)
        }
        TodayCountdown.AllDay -> stringResource(R.string.stream_today_countdown_all_day)
        TodayCountdown.Ended -> stringResource(R.string.stream_today_countdown_ended)
    }

/**
 * "To do today": the tasks due today and overdue as a checklist with how many are left, a checkbox that completes one
 * through the Tasks action (the source ticks it at once and puts it back if Todoist refuses), and the add field.
 */
@Composable
internal fun TodoCard(
    card: TodayCard.ToDo,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    StreamCardSurface("today:todo", null, null, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .testTag(TODAY_TODO_TEST_TAG)
                    .padding(
                        start = RiffleSpacing.l,
                        end = RiffleSpacing.l,
                        top = StreamHeaderTopPadding,
                        bottom = RiffleSpacing.s,
                    ),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
        ) {
            StreamCardHeader(
                icon = { StreamGlyph(Icons.Filled.CheckCircle) },
                title = stringResource(R.string.stream_today_todo_title),
                modifier = Modifier.semantics { heading() },
                trailing = {
                    Text(
                        text = todoCountsText(card),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (card.overdue > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                },
            )
            if (card.tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.stream_today_todo_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = StreamMinTarget),
                )
            }
            card.tasks.take(MAX_TASKS_ON_TODO_CARD).forEach { task -> TaskLine(task, env, callbacks) }
            val more = card.tasks.size - MAX_TASKS_ON_TODO_CARD
            if (more > 0 || card.tasks.any(TaskContent::stale)) {
                Text(
                    text =
                        if (card.tasks.any(TaskContent::stale)) {
                            stringResource(R.string.stream_task_stale)
                        } else {
                            stringResource(R.string.stream_more_count, more)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.weight(1f))
            TaskQuickAdd(callbacks)
        }
    }
}

/** Tomorrow in one card: how many events and tasks and the first event; a tap opens it (else the first task). */
@Composable
internal fun TomorrowCard(
    card: TodayCard.Tomorrow,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val open = card.firstEvent ?: card.firstTask
    StreamCardSurface("today:tomorrow", null, null, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .testTag(TODAY_TOMORROW_TEST_TAG)
                    .then(
                        if (open != null) {
                            Modifier.streamClickable({ callbacks.onAction(open, StreamAction.Open) })
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = RiffleSpacing.l,
                        end = RiffleSpacing.l,
                        top = StreamHeaderTopPadding,
                        bottom = RiffleSpacing.l,
                    ),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            StreamCardHeader(
                icon = { StreamGlyph(StreamIcons.Agenda) },
                title = stringResource(R.string.stream_today_tomorrow_title),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = tomorrowCountsText(card),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            card.firstEvent?.let { first ->
                Text(
                    text = tomorrowFirstText(first, env),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A notice as a card of a stack: thin glass, one sentence and its action. */
@Composable
internal fun NoticeCard(
    notice: Notice,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier,
        weight = GlassWeight.Thin,
        shape = RiffleShapes.medium,
        tint = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize().padding(
                    start = RiffleSpacing.l,
                    end = RiffleSpacing.l,
                    top = StreamHeaderTopPadding,
                    bottom = RiffleSpacing.l,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            // From the top: when the card is one behind the front, its strip shows its icon and its title.
            verticalArrangement = Arrangement.Top,
        ) {
            val action = noticeAction(notice, callbacks)
            StreamCardHeader(
                icon = { StreamGlyph(Icons.Filled.Info) },
                title = noticeTitle(notice),
            )
            Text(
                text = noticeText(notice),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                FilledTonalButton(
                    onClick = action.second,
                    modifier = Modifier.padding(top = RiffleSpacing.s).heightIn(min = StreamMinTarget),
                ) { Text(action.first) }
            }
        }
    }
}

/** The one-line name of a notice, for the strip of its card: the sentence itself is the card's body. */
@Composable
internal fun noticeTitle(notice: Notice): String =
    when (notice) {
        Notice.ShortcutsNeedHome -> stringResource(R.string.stream_notice_shortcuts_title)
        is Notice.Today ->
            when (val today = notice.notice) {
                is TodayNotice.Permission -> stringResource(R.string.stream_notice_permission_title)
                is TodayNotice.TasksNotConnected -> stringResource(R.string.stream_notice_tasks_not_connected_title)
                TodayNotice.TasksUnavailable -> stringResource(R.string.stream_notice_tasks_unavailable_title)
            }
    }

@Composable
internal fun noticeText(notice: Notice): String =
    when (notice) {
        Notice.ShortcutsNeedHome -> stringResource(R.string.stream_shortcuts_need_home)
        is Notice.Today ->
            when (val today = notice.notice) {
                is TodayNotice.Permission -> stringResource(R.string.stream_permission_message)
                is TodayNotice.TasksNotConnected -> tasksNotConnectedMessage(today.reason)
                TodayNotice.TasksUnavailable -> stringResource(R.string.stream_tasks_unavailable)
            }
    }

/** The label and the action of a notice's button, or null when it has none. */
@Composable
private fun noticeAction(
    notice: Notice,
    callbacks: StreamCallbacks,
): Pair<String, () -> Unit>? =
    when (notice) {
        Notice.ShortcutsNeedHome -> null
        is Notice.Today ->
            when (val today = notice.notice) {
                is TodayNotice.Permission ->
                    stringResource(R.string.stream_permission_action) to { callbacks.onRequestAccess(today.sources) }
                is TodayNotice.TasksNotConnected ->
                    stringResource(
                        if (today.reason == NotConnectedReason.TOKEN_REJECTED) {
                            R.string.stream_tasks_replace_token
                        } else {
                            R.string.stream_tasks_connect
                        },
                    ) to callbacks.onConnectTasks
                TodayNotice.TasksUnavailable ->
                    stringResource(R.string.stream_task_retry) to callbacks.onRefreshTasks
            }
    }

@Composable
internal fun tasksNotConnectedMessage(reason: NotConnectedReason): String =
    stringResource(
        if (reason == NotConnectedReason.TOKEN_REJECTED) {
            R.string.stream_tasks_token_rejected
        } else {
            R.string.stream_not_connected_tasks
        },
    )

/**
 * Today as a List ("Show as"): the old Agenda list, its days under their headings, followed by the old Tasks list
 * (the add field, the tasks, the stale note) under its heading, then what could not be shown.
 */
@Composable
internal fun TodayListBody(
    body: StreamPageBody.Content,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    val eventGroups = body.groups.filter { it.key != StreamGroupKeys.TASKS }
    val tasks = body.groups.firstOrNull { it.key == StreamGroupKeys.TASKS }?.items.orEmpty()
    val tasksConnected =
        tasks.isNotEmpty() ||
            body.gaps.any { it.source == StreamSourceIds.TASKS && it.state == StreamSectionResult.Empty }
    PageScroll {
        if (eventGroups.isNotEmpty()) {
            SectionHeading(stringResource(R.string.stream_today_agenda_heading))
            eventGroups.forEach { group ->
                GroupHeading(StreamSection.TODAY, group, env)
                StreamRowsPanel(StreamSection.TODAY, group.items, env, callbacks, null)
            }
        }
        if (tasksConnected) {
            SectionHeading(stringResource(R.string.stream_today_tasks_heading))
            TaskQuickAdd(callbacks)
            if (tasks.isNotEmpty()) StreamRowsPanel(StreamSection.TODAY, tasks, env, callbacks, null)
            if (tasks.any { it is TaskContent && it.stale }) TaskStaleNote(callbacks)
        }
        body.gaps.mapNotNull(::noticeOf).forEach { notice ->
            val action = noticeAction(notice, callbacks)
            StreamStateMessage(
                message = noticeText(notice),
                actionLabel = action?.first,
                onAction = { action?.second?.invoke() },
            )
        }
    }
}

private fun noticeOf(gap: com.riffle.core.domain.launcher.stream.StreamGap): Notice? =
    when (val state = gap.state) {
        is StreamSectionResult.PermissionRequired -> Notice.Today(TodayNotice.Permission(state.sources))
        is StreamSectionResult.NotConnected -> Notice.Today(TodayNotice.TasksNotConnected(state.reason))
        StreamSectionResult.Unavailable ->
            Notice.Today(TodayNotice.TasksUnavailable).takeIf { gap.source == StreamSourceIds.TASKS }
        else -> null
    }

@Composable
private fun SectionHeading(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GlassSurface(
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier
                        .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs)
                        .semantics { heading() },
            )
        }
    }
}

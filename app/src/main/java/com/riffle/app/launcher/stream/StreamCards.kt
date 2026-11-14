@file:Suppress("TooManyFunctions", "MatchingDeclarationName")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassCardTint
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glassSpotlightSource
import com.riffle.app.launcher.rememberAppIconColor
import com.riffle.app.launcher.rememberGlassSpotlightColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.ArticleAction
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.EventTiming
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/** The collapsed height each card type is drawn at in a stack (scaled up with the font size). */
internal object StreamCardHeights {
    val notification: Dp = StreamCardStackSpec.CARD_HEIGHT_DP.dp
    val media: Dp = 264.dp
    val event: Dp = StreamCardStackSpec.CARD_HEIGHT_DP.dp
    val article: Dp = 248.dp
    val articleWithoutImage: Dp = StreamCardStackSpec.CARD_HEIGHT_DP.dp
    val tasks: Dp = 232.dp

    /** An app (a recent app, a shortcut): icon and a line or two. */
    val app: Dp = 104.dp

    /** Today's front card: the event, its countdown and its actions. */
    val nextEvent: Dp = 200.dp

    /** Today's checklist: a header, up to three tasks and the add field. */
    val todo: Dp = 288.dp

    /** Tomorrow's summary. */
    val summary: Dp = 144.dp

    /** A notice: one honest sentence and its action. */
    val notice: Dp = 144.dp
}

private val LeadImageHeight = 112.dp
private const val BODY_LINES = 2

/**
 * The glass every Stream card is drawn on: regular weight in the launcher's card shape, lit by the colour of the app
 * the content belongs to (no app, no spotlight: the sheen only). [body] runs inside the glass, so the spotlight
 * source can be reported from there.
 */
@Composable
internal fun StreamCardSurface(
    key: String,
    app: StreamAppRef?,
    icon: ItemImageHandle?,
    env: StreamEnvironment,
    modifier: Modifier = Modifier,
    weight: GlassWeight = GlassWeight.Regular,
    body: @Composable (Modifier) -> Unit,
) {
    val identity = remember(app, icon, env.installedApps) { env.identityFor(app, icon) }
    val appColor = rememberAppIconColor(identity, env.appIconLoader)
    GlassSurface(
        modifier = modifier,
        weight = weight,
        shape = LocalLauncherCardShape.current,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        // The app's colour is in the fill (and, if the setting asks, a glow along the top edge): not also an edge glow,
        // which read as a coloured outline. The content still reports its colour to a spotlit surface it sits in.
        spotlight = false,
        cardTint = GlassCardTint(appColor, env.cardSettings.cardTintPercent, env.cardSettings.cardTopGlow),
    ) {
        val color = rememberGlassSpotlightColor(identity = identity, appIconLoader = env.appIconLoader)
        body(Modifier.glassSpotlightSource(key = key, color = color))
    }
}

@Composable
internal fun hiddenText(): String = stringResource(R.string.stream_hidden_content)

/**
 * A notification on a browse card (ADR 0003, Amendment 1, "Browse"): the contact photo as a round avatar with the app's
 * icon badged at its corner, who and when, the message and, when the notification carries a picture, one small
 * thumbnail at the trailing edge. A tap opens it in place ([onOpen]); without one (a surface that cannot open a card in
 * place) it opens the app. Its actions are on the open card: the Dock's row, or the card itself.
 */
@Composable
@Suppress("LongMethod")
internal fun NotificationCard(
    content: NotificationContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    StreamCardSurface(content.id.value, content.app, content.icon, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxWidth()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .streamClickable(onOpen ?: { callbacks.onAction(content, StreamAction.Open) })
                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                StreamAvatar(content.avatar, content.icon, content.title ?: content.appLabel, env, BrowseAvatarSize)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
                    Text(
                        text = content.title ?: hiddenText(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                content.appLabel?.takeIf { it.isNotBlank() },
                                env.formats.age(content.postedAtEpochMillis),
                            ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                content.text?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = BODY_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Box(Modifier.weight(1f))
                StreamThumbnail(content.picture, content.title, env, BrowseThumbnailSize)
            }
        }
    }
}

private val BrowseAvatarSize = 48.dp
private val BrowseThumbnailSize = 64.dp

/** The message history of a messaging notification, oldest first. Nothing when the notification keeps none. */
@Composable
internal fun NotificationThread(content: NotificationContent) {
    if (content.thread.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        content.thread.takeLast(MAX_THREAD_LINES).forEach { message ->
            Text(
                text =
                    buildString {
                        append(message.sender ?: stringResource(R.string.stream_notification_you))
                        append(": ")
                        append(message.text)
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val MAX_THREAD_LINES = 5

/** An event: its time span, title, place and a countdown to the start. A calendar feed shows its feed name. */
@Composable
internal fun EventCard(
    content: EventContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    /** The day, said before the time when a pile of events spans days. */
    caption: String? = null,
    /** A tap opens the event in place; without one (a widget) it opens the event in the calendar. */
    onOpen: (() -> Unit)? = null,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    StreamCardSurface(content.id.value, null, null, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxWidth()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .streamClickable(onOpen ?: { callbacks.onAction(content, StreamAction.Open) })
                    .padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(
                    text = eventSpanText(content, env).let { span -> if (caption == null) span else "$caption, $span" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
            }
            Text(
                text = eventTitle(content),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
            Text(
                text = eventCountdownText(content, env.nowEpochMillis),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun eventTitle(content: EventContent): String =
    content.title?.takeIf { it.isNotBlank() }
        ?: if (content.privacy == StreamPrivacy.REDACTED) {
            hiddenText()
        } else if (content.kind == EventKind.ALARM) {
            stringResource(R.string.stream_event_alarm)
        } else {
            stringResource(R.string.stream_event_untitled)
        }

/** "09:00 to 10:00", "All day", or the alarm's time. */
@Composable
internal fun eventSpanText(
    content: EventContent,
    env: StreamEnvironment,
): String =
    when {
        content.kind == EventKind.ALARM -> env.formats.time(content.startEpochMillis)
        content.allDay -> stringResource(R.string.stream_event_all_day)
        content.endEpochMillis != null ->
            stringResource(
                R.string.stream_event_time_span,
                env.formats.time(content.startEpochMillis),
                env.formats.time(content.endEpochMillis!!),
            )
        else -> env.formats.time(content.startEpochMillis)
    }

/** "Starts in 12 minutes", "Now", "30 minutes left", "Ended". */
@Composable
internal fun eventCountdownText(
    content: EventContent,
    nowEpochMillis: Long,
): String =
    when (val timing = EventTiming.of(content, nowEpochMillis)) {
        is EventTiming.Upcoming ->
            if (timing.minutes >= MINUTES_PER_HOUR * 2) {
                val hours = ((timing.minutes + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR).toInt()
                pluralStringResource(R.plurals.stream_event_starts_in_hours, hours, hours)
            } else {
                pluralStringResource(
                    R.plurals.stream_event_starts_in_minutes,
                    timing.minutes.toInt(),
                    timing.minutes.toInt(),
                )
            }
        is EventTiming.Ongoing ->
            timing.minutesLeft?.let { left ->
                pluralStringResource(R.plurals.stream_event_minutes_left, left.toInt(), left.toInt())
            } ?: stringResource(R.string.stream_event_now)
        EventTiming.Ended -> stringResource(R.string.stream_event_ended)
        EventTiming.AllDay -> stringResource(R.string.stream_event_all_day)
    }

private const val MINUTES_PER_HOUR = 60L

/** An article: lead image, headline, feed name and age. */
@Composable
internal fun ArticleCard(
    content: ArticleContent,
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
                    .fillMaxWidth()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().streamClickable({ callbacks.onAction(content, StreamAction.Open) }),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                StreamLeadImage(content.image, env, LeadImageHeight)
                Text(
                    text = content.title ?: hiddenText(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (content.read) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(
                    text = articleMeta(content, env),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (content.canMarkRead()) {
                    StreamTextButton(
                        stringResource(R.string.stream_article_mark_read),
                        { callbacks.onAction(content, StreamAction.MarkRead) },
                    )
                }
                StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
            }
        }
    }
}

private fun ArticleContent.canMarkRead(): Boolean =
    StreamActionPolicy.offers(this, StreamAction.MarkRead) && ArticleAction.MARK_READ in actions

/** "Feed name, 5 min. ago". */
@Composable
internal fun articleMeta(
    content: ArticleContent,
    env: StreamEnvironment,
): String =
    listOfNotNull(
        content.feedName?.takeIf { it.isNotBlank() },
        content.publishedAtEpochMillis?.let(env.formats::age),
    ).joinToString(", ")

/**
 * Today's tasks as one card: a checkbox, the title and the due time for each. The checkbox completes the task (the
 * source ticks it at once and puts it back if Todoist refuses); the title opens it.
 */
@Composable
internal fun TasksCard(
    tasks: List<TaskContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    StreamCardSurface("tasks", null, null, env, modifier) { sourceModifier ->
        Column(
            modifier = sourceModifier.fillMaxWidth().padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
        ) {
            tasks.take(MAX_TASKS_ON_CARD).forEach { task -> TaskLine(task, env, callbacks) }
        }
    }
}

private const val MAX_TASKS_ON_CARD = 4

@Composable
internal fun TaskLine(
    task: TaskContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val canComplete = StreamActionPolicy.offers(task, StreamAction.Complete)
    val canOpen = StreamActionPolicy.offers(task, StreamAction.Open)
    val title = task.title ?: hiddenText()
    val completeLabel = stringResource(R.string.stream_task_complete, title)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = StreamMinTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        Checkbox(
            checked = task.completed,
            onCheckedChange = { checked -> if (checked) callbacks.onAction(task, StreamAction.Complete) },
            enabled = canComplete,
            modifier = Modifier.semantics { contentDescription = completeLabel },
        )
        Column(
            modifier =
                Modifier.weight(1f).heightIn(min = StreamMinTarget).then(
                    if (canOpen) Modifier.clickable { callbacks.onAction(task, StreamAction.Open) } else Modifier,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            taskDueText(task, env)?.let { due ->
                Text(
                    text = due,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (task.overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
internal fun taskDueLabel(
    task: TaskContent,
    env: StreamEnvironment,
): String? = taskDueText(task, env)

@Composable
private fun taskDueText(
    task: TaskContent,
    env: StreamEnvironment,
): String? {
    val due = task.dueEpochMillis ?: return null
    val whenText = if (task.dueHasTime) env.formats.time(due) else env.formats.age(due)
    return when {
        task.overdue -> stringResource(R.string.stream_task_overdue, whenText)
        task.dueHasTime -> whenText
        else -> stringResource(R.string.stream_task_due, whenText)
    }
}

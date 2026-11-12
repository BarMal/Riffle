@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.ArticleAction
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent

private val RowIconSize = RiffleSpacing.xxxl - RiffleSpacing.s
private val RowArtworkSize = RiffleSpacing.xxxl - RiffleSpacing.xs

/** The shared frame of a List row: a 48 dp minimum, the Hide menu and its accessibility actions. */
@Composable
private fun StreamRowFrame(
    content: StreamContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    body: @Composable () -> Unit,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = StreamMinTarget)
                .streamHideActions(hideChoices, content, groupKey, callbacks)
                .padding(start = RiffleSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = StreamMinTarget)
                    .streamClickable(onClick)
                    .padding(vertical = RiffleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) { body() }
        }
        trailing()
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
}

@Composable
private fun RowTitle(
    text: String,
    dimmed: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
    )
}

@Composable
private fun RowSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** App icon, sender, one line of the message and the time; a dismiss button stands for the swipe. */
@Composable
internal fun NotificationRow(
    content: NotificationContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    StreamRowFrame(
        content = content,
        groupKey = groupKey,
        callbacks = callbacks,
        onClick = { callbacks.onAction(content, StreamAction.Open) },
        modifier = modifier,
        leading = { StreamIcon(content.icon, env, RowIconSize) },
        trailing = {
            Text(
                text = env.formats.age(content.postedAtEpochMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (StreamActionPolicy.offers(content, StreamAction.Dismiss)) {
                IconButton(
                    onClick = { callbacks.onAction(content, StreamAction.Dismiss) },
                    modifier = Modifier.size(StreamMinTarget),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stream_notification_dismiss))
                }
            }
        },
    ) {
        RowTitle(content.title ?: stringResource(R.string.stream_hidden_content))
        content.text?.takeIf { it.isNotBlank() }?.let { RowSubtitle(it) }
    }
}

/** Small artwork, the title and a play or pause button. */
@Composable
internal fun MediaRow(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    val playing = content.isPlaying == true
    val toggle = if (playing) StreamAction.Pause else StreamAction.Play
    StreamRowFrame(
        content = content,
        groupKey = groupKey,
        callbacks = callbacks,
        onClick = { callbacks.onAction(content, StreamAction.Open) },
        modifier = modifier,
        leading = { StreamIcon(content.artwork ?: content.icon, env, RowArtworkSize, shape = RiffleShapes.small) },
        trailing = {
            IconButton(
                onClick = { callbacks.onAction(content, toggle) },
                enabled = StreamActionPolicy.offers(content, toggle),
                modifier = Modifier.size(StreamMinTarget),
            ) {
                Icon(
                    imageVector = if (playing) StreamIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription =
                        stringResource(if (playing) R.string.stream_media_pause else R.string.stream_media_play),
                )
            }
        },
    ) {
        RowTitle(content.title ?: stringResource(R.string.stream_hidden_content))
        content.artist?.takeIf { it.isNotBlank() }?.let { RowSubtitle(it) }
    }
}

/** The time, the title and the place or calendar; the next one carries its countdown. */
@Composable
internal fun EventRow(
    content: EventContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    showCountdown: Boolean = false,
) {
    StreamRowFrame(
        content = content,
        groupKey = groupKey,
        callbacks = callbacks,
        onClick = { callbacks.onAction(content, StreamAction.Open) },
        modifier = modifier,
        leading = {
            Text(
                text = eventLeadingTime(content, env),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = RowIconSize),
            )
        },
    ) {
        RowTitle(eventTitle(content))
        val detail =
            listOfNotNull(
                (content.location ?: content.calendarName)?.takeIf { it.isNotBlank() },
                eventCountdownText(content, env.nowEpochMillis).takeIf { showCountdown },
            ).joinToString(", ")
        if (detail.isNotEmpty()) RowSubtitle(detail)
    }
}

@Composable
private fun eventLeadingTime(
    content: EventContent,
    env: StreamEnvironment,
): String =
    if (content.allDay && content.kind != EventKind.ALARM) {
        stringResource(R.string.stream_event_all_day)
    } else {
        env.formats.time(content.startEpochMillis)
    }

/** Headline, feed name and age; read articles are dimmed. */
@Composable
internal fun ArticleRow(
    content: ArticleContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    val canMarkRead =
        StreamActionPolicy.offers(content, StreamAction.MarkRead) && ArticleAction.MARK_READ in content.actions
    StreamRowFrame(
        content = content,
        groupKey = groupKey,
        callbacks = callbacks,
        onClick = { callbacks.onAction(content, StreamAction.Open) },
        modifier = modifier,
        leading = {},
        trailing = {
            if (canMarkRead) {
                StreamTextButton(
                    stringResource(R.string.stream_article_mark_read),
                    { callbacks.onAction(content, StreamAction.MarkRead) },
                )
            }
        },
    ) {
        Text(
            text = content.title ?: stringResource(R.string.stream_hidden_content),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (content.read) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
        articleMeta(content, env).takeIf { it.isNotEmpty() }?.let { RowSubtitle(it) }
    }
}

/** A checkbox, the title and the due time. */
@Composable
internal fun TaskRow(
    task: TaskContent,
    env: StreamEnvironment,
    modifier: Modifier = Modifier,
) {
    TaskLine(task, env, modifier.padding(horizontal = RiffleSpacing.s))
}

/** Icon and label, with when the app was last used. */
@Composable
internal fun RecentAppRow(
    content: RecentAppContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    StreamRowFrame(
        content = content,
        groupKey = groupKey,
        callbacks = callbacks,
        onClick = { callbacks.onAction(content, StreamAction.Open) },
        modifier = modifier,
        leading = { StreamIcon(content.icon, env, RowIconSize) },
    ) {
        RowTitle(content.label ?: stringResource(R.string.stream_hidden_content))
        content.lastUsedEpochMillis?.let { used ->
            RowSubtitle(stringResource(R.string.stream_recent_last_used, env.formats.age(used)))
        }
    }
}

/** The row for any content, so a section draws a List without knowing what is in it. */
@Composable
internal fun StreamRow(
    content: StreamContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    showCountdown: Boolean = false,
) {
    when (content) {
        is NotificationContent -> NotificationRow(content, env, callbacks, groupKey)
        is MediaContent -> MediaRow(content, env, callbacks, groupKey)
        is EventContent -> EventRow(content, env, callbacks, groupKey, showCountdown = showCountdown)
        is ArticleContent -> ArticleRow(content, env, callbacks, groupKey)
        is TaskContent -> TaskRow(content, env)
        is RecentAppContent -> RecentAppRow(content, env, callbacks, groupKey)
    }
}

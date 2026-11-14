package com.riffle.app.launcher.stream

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent

private val OpenAvatarSize = 56.dp
private val OpenPictureMaxHeight = 280.dp
private val OpenArtworkMaxWidth = 320.dp

/**
 * A card opened in place (ADR 0003, Amendment 1, "Stream: browse, then open"): its full content, on the same tinted
 * glass as its browse card. A conversation is its thread and its picture in full; the player is its artwork, scrubber
 * and queue; an event its detail; a task its title and due date. Its actions are in the Dock's row, or, when
 * [actionsOnCard], on the card under its content; either way the card carries them as accessibility actions, so a
 * screen reader on it never has to leave it. A tap on the card closes it ([onClose]); the controls on it keep their
 * own taps. Taller than the room there is, it scrolls.
 */
@Composable
@Suppress("LongParameterList")
internal fun OpenStreamCard(
    content: StreamContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    actionsOnCard: Boolean,
    queueShown: Boolean,
    onQueueShownChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    val actions = StreamCardActions.forContent(content, queueShown)
    StreamCardSurface(content.id.value, content.appRef(), content.iconHandle(), env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxWidth()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .cardActionAccessibility(content, actions, callbacks, onQueueShownChange)
                    .streamClickable(onClose, stringResource(R.string.stream_card_close))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            when (content) {
                is NotificationContent -> OpenNotification(content, env, callbacks, groupKey, hideChoices)
                is MediaContent ->
                    OpenMedia(content, env, callbacks, groupKey, hideChoices, queueShown)
                is EventContent -> OpenEvent(content, env, callbacks, groupKey, hideChoices)
                is TaskContent -> OpenTask(content, env)
                else -> Unit
            }
            if (actionsOnCard) {
                CardActionControls(content, actions, callbacks, onQueueShownChange)
            }
        }
    }
}

@Composable
private fun OpenNotification(
    content: NotificationContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        StreamAvatar(content.avatar, content.icon, content.title ?: content.appLabel, env, OpenAvatarSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.title ?: hiddenText(),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    listOfNotNull(
                        content.appLabel?.takeIf { it.isNotBlank() },
                        env.formats.age(content.postedAtEpochMillis),
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
    if (content.thread.isNotEmpty()) {
        NotificationThread(content)
    } else {
        content.text?.takeIf { it.isNotBlank() }?.let { text ->
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
    // The picture the app attached, in full under the conversation it belongs to.
    val sender = content.thread.lastOrNull()?.sender ?: content.title
    StreamPicture(content.picture, sender, env, OpenPictureMaxHeight)
    if (StreamActionPolicy.offers(content, StreamAction.Open)) {
        StreamTextButton(stringResource(R.string.stream_open), { callbacks.onAction(content, StreamAction.Open) })
    }
}

@Composable
private fun OpenMedia(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
    queueShown: Boolean,
) {
    // The artwork large and square, only once it has loaded: a player that publishes none leaves no empty square.
    val artwork = rememberStreamImage(content.artwork, env.imageLoader, OpenArtworkMaxWidth).value
    if (artwork != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = artwork,
                contentDescription = stringResource(R.string.stream_artwork),
                modifier =
                    Modifier
                        .widthIn(max = OpenArtworkMaxWidth)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RiffleShapes.medium),
                contentScale = ContentScale.Crop,
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        StreamIcon(content.icon, env, RiffleSpacing.xl, shape = RiffleShapes.small, lightsCard = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.title ?: stringResource(R.string.stream_hidden_content),
                style = MaterialTheme.typography.titleLarge,
            )
            content.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
    MediaScrubber(content, callbacks)
    if (queueShown) MediaQueue(content, env)
    if (StreamActionPolicy.offers(content, StreamAction.Open)) {
        StreamTextButton(
            stringResource(R.string.stream_media_open_player),
            { callbacks.onAction(content, StreamAction.Open) },
        )
    }
}

@Composable
private fun OpenEvent(
    content: EventContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        Text(
            text = eventSpanText(content, env),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
    Text(text = eventTitle(content), style = MaterialTheme.typography.headlineSmall)
    (content.location ?: content.calendarName)?.takeIf { it.isNotBlank() }?.let { place ->
        Text(
            text = place,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = eventCountdownText(content, env.nowEpochMillis),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun OpenTask(
    content: TaskContent,
    env: StreamEnvironment,
) {
    Text(text = content.title ?: hiddenText(), style = MaterialTheme.typography.headlineSmall)
    content.listName?.takeIf { it.isNotBlank() }?.let { list ->
        Text(
            text = list,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    taskDueLabel(content, env)?.let { due -> Text(text = due, style = MaterialTheme.typography.bodyMedium) }
}

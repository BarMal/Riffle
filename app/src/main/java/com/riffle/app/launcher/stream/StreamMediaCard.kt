package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.MediaProgress
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.workspace.settings.HideChoice

private const val MAX_QUEUE_ROWS = 8
private const val SCRUBBER_ACTIVE_ALPHA = 0.7f
private const val SCRUBBER_INACTIVE_ALPHA = 0.2f
private val QueueArtworkSize = RiffleSpacing.xxxl - RiffleSpacing.s

/**
 * Now playing on a browse card: the artwork is the card's surface, a thick glass deck over it holds the title, the
 * scrubber and the transport. Every control is a plain button or slider (48 dp), so nothing here needs a gesture. A tap
 * on the title opens the player in place ([onOpen]: the queue and the large artwork are on the open card); without one
 * (a widget) it opens the player app.
 */
@Composable
internal fun MediaCard(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    StreamCardSurface(content.id.value, content.app, content.icon, env, modifier) { sourceModifier ->
        Box(
            modifier = sourceModifier.fillMaxSize().streamHideActions(hideChoices, content, groupKey, callbacks),
        ) {
            StreamArtwork(
                content.artwork,
                env,
                sizeHint = StreamCardHeights.media,
                modifier = Modifier.matchParentSize(),
            )
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // The top of the card: the app's icon and the track, so the strip behind another card says what plays.
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    weight = GlassWeight.Thick,
                    shape = RectangleShape,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    MediaHeader(content, env, callbacks, groupKey, hideChoices, onOpen)
                }
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    weight = GlassWeight.Thick,
                    shape = RectangleShape,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    MediaDeck(content, callbacks)
                }
            }
        }
    }
}

@Composable
private fun MediaHeader(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<HideChoice>,
    onOpen: (() -> Unit)?,
) {
    StreamCardHeader(
        icon = { StreamIcon(content.icon, env, StreamHeaderIconSize, shape = RiffleShapes.small, lightsCard = true) },
        title = content.title ?: stringResource(R.string.stream_hidden_content),
        subtitle = content.artist,
        modifier =
            Modifier.padding(
                start = RiffleSpacing.l,
                end = RiffleSpacing.xs,
                top = StreamHeaderTopPadding,
                bottom = RiffleSpacing.xs,
            ),
        contentModifier =
            Modifier.streamClickable(
                onOpen ?: { callbacks.onAction(content, StreamAction.Open) },
                stringResource(if (onOpen != null) R.string.stream_open_card else R.string.stream_media_open_player),
            ),
        trailing = { StreamHideMenuButton(hideChoices, content, groupKey, callbacks) },
    )
}

@Composable
private fun MediaDeck(
    content: MediaContent,
    callbacks: StreamCallbacks,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
    ) {
        MediaScrubber(content, callbacks)
        MediaTransport(content, callbacks)
    }
}

/** The position slider; read-only while the session offers no seeking, and absent without a duration. */
@Composable
internal fun MediaScrubber(
    content: MediaContent,
    callbacks: StreamCallbacks,
) {
    val duration = content.durationMillis ?: return
    val progress = MediaProgress.fraction(content.positionMillis, duration) ?: return
    var dragging by remember(content.id) { mutableStateOf<Float?>(null) }
    val shown = dragging ?: progress
    val seekable = StreamActionPolicy.offers(content, StreamAction.SeekTo(0L))
    val elapsed = MediaProgress.clock(MediaProgress.positionAt(shown, duration))
    val total = MediaProgress.clock(duration)
    val position = stringResource(R.string.stream_media_position)
    val value = stringResource(R.string.stream_media_position_value, elapsed, total)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Text(elapsed, style = MaterialTheme.typography.labelSmall)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let {
                    callbacks.onAction(
                        content,
                        StreamAction.SeekTo(MediaProgress.positionAt(it, duration)),
                    )
                }
                dragging = null
            },
            enabled = seekable,
            // The card's ink, softened: the theme's accent (which the card's text takes as its ink) is too heavy here.
            colors =
                SliderDefaults.colors(
                    thumbColor = LocalContentColor.current,
                    activeTrackColor = LocalContentColor.current.copy(alpha = SCRUBBER_ACTIVE_ALPHA),
                    inactiveTrackColor = LocalContentColor.current.copy(alpha = SCRUBBER_INACTIVE_ALPHA),
                    disabledThumbColor = LocalContentColor.current.copy(alpha = SCRUBBER_ACTIVE_ALPHA),
                    disabledActiveTrackColor = LocalContentColor.current.copy(alpha = SCRUBBER_ACTIVE_ALPHA),
                    disabledInactiveTrackColor = LocalContentColor.current.copy(alpha = SCRUBBER_INACTIVE_ALPHA),
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = position
                        stateDescription = value
                    },
        )
        Text(total, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MediaTransport(
    content: MediaContent,
    callbacks: StreamCallbacks,
) {
    val playing = content.isPlaying == true
    val toggle = if (playing) StreamAction.Pause else StreamAction.Play
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TransportButton(
            StreamIcons.SkipPrevious,
            stringResource(R.string.stream_media_previous),
            StreamActionPolicy.offers(content, StreamAction.Previous),
        ) { callbacks.onAction(content, StreamAction.Previous) }
        TransportButton(
            if (playing) StreamIcons.Pause else Icons.Filled.PlayArrow,
            stringResource(if (playing) R.string.stream_media_pause else R.string.stream_media_play),
            StreamActionPolicy.offers(content, toggle),
        ) { callbacks.onAction(content, toggle) }
        TransportButton(
            StreamIcons.SkipNext,
            stringResource(R.string.stream_media_next),
            StreamActionPolicy.offers(content, StreamAction.Next),
        ) { callbacks.onAction(content, StreamAction.Next) }
    }
}

@Composable
internal fun TransportButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(StreamMinTarget)) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(28.dp))
    }
}

@Composable
internal fun MediaQueue(
    content: MediaContent,
    env: StreamEnvironment,
) {
    if (content.queue.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        Text(
            text = stringResource(R.string.stream_media_up_next),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content.queue.take(MAX_QUEUE_ROWS).forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                entry.artwork?.let { StreamIcon(it, env, QueueArtworkSize) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title ?: stringResource(R.string.stream_media_unknown_title),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

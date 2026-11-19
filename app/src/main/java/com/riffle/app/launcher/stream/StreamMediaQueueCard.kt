package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.workspace.settings.HideChoice

/** The action a queue card performs: play its track. Null for a card that is not a queue card. */
internal fun MediaContent.playQueueAction(): StreamAction? = queueItem?.let { StreamAction.PlayQueueItem(it.queueId) }

/** The queue card as one spoken phrase: "Up next, <track> by <artist>". */
@Composable
internal fun MediaContent.queueSpoken(): String {
    val track = title ?: stringResource(R.string.stream_media_unknown_title)
    val by = artist?.takeIf { it.isNotBlank() }
    return if (by != null) {
        stringResource(R.string.stream_media_queue_spoken_by, track, by)
    } else {
        stringResource(R.string.stream_media_queue_spoken, track)
    }
}

/**
 * One upcoming track of a player's queue, a card behind the player's own (ADR 0003, Amendment 16): its artwork (else
 * the app's icon), the track and its artist, and one primary action, Play, which skips the session to it. Like every
 * card, its header is what a strip of it shows behind the front card. The stack's own gestures browse these cards; the
 * card is one node to assistive technology with Play and Hide as actions.
 */
@Composable
internal fun MediaQueueCard(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    val play = content.playQueueAction()
    val playLabel = stringResource(R.string.stream_media_source_play)
    val spoken = content.queueSpoken()
    StreamCardSurface(content.id.value, content.app, content.icon, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        customActions = queueCardActions(content, groupKey, callbacks, hideChoices, play, playLabel)
                    }
                    .padding(start = RiffleSpacing.l, top = StreamHeaderTopPadding, bottom = RiffleSpacing.s),
        ) {
            StreamCardHeader(
                icon = {
                    StreamIcon(
                        content.artwork ?: content.icon,
                        env,
                        StreamHeaderIconSize,
                        lightsCard = content.artwork == null,
                    )
                },
                title = content.title ?: stringResource(R.string.stream_media_unknown_title),
                subtitle = content.artist,
                modifier = Modifier.padding(end = RiffleSpacing.xs),
                contentModifier = Modifier.streamClickable({ play?.let { callbacks.onAction(content, it) } }),
                trailing = { StreamHideMenuButton(hideChoices, content, groupKey, callbacks) },
            )
            // A gap under the header: the strip of this card behind another shows the header only, never the button.
            Spacer(modifier = Modifier.height(RiffleSpacing.s))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = RiffleSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                FilledTonalButton(
                    onClick = { play?.let { callbacks.onAction(content, it) } },
                    modifier = Modifier.heightIn(min = StreamMinTarget),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(playLabel, maxLines = 1)
                }
            }
        }
    }
}

private fun queueCardActions(
    content: MediaContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
    hideChoices: List<HideChoice>,
    play: StreamAction?,
    playLabel: String,
): List<CustomAccessibilityAction> =
    buildList {
        if (play != null) {
            add(
                CustomAccessibilityAction(playLabel) {
                    callbacks.onAction(content, play)
                    true
                },
            )
        }
        callbacks.onHide?.let { onHide ->
            hideChoices.forEach { choice ->
                add(
                    CustomAccessibilityAction(choice.label) {
                        onHide(content, groupKey, choice.kind)
                        true
                    },
                )
            }
        }
    }

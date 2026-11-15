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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.workspace.settings.HideChoice

/** What a recent player's card says of what it last played ("Song, Band, 5 min. ago"), or when it last played. */
@Composable
internal fun MediaContent.sourceSubtitle(env: StreamEnvironment): String {
    val age = postedAtEpochMillis?.let(env.formats::age)
    val played =
        listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() }).joinToString(", ")
    return when {
        played.isNotEmpty() && age != null -> stringResource(R.string.stream_media_source_played_ago, played, age)
        played.isNotEmpty() -> played
        age != null -> stringResource(R.string.stream_media_source_last_played, age)
        else -> ""
    }
}

/** The recent player as one spoken phrase: "<app>, last played <time>". */
@Composable
internal fun MediaContent.mediaSourceSpoken(env: StreamEnvironment): List<String> {
    val name = appLabel ?: hiddenText()
    val age = postedAtEpochMillis?.let(env.formats::age)
    return listOf(
        if (age != null) {
            stringResource(R.string.stream_media_source_spoken, name, age)
        } else {
            stringResource(R.string.stream_media_source_spoken_unknown, name)
        },
    )
}

/**
 * A recent player: an app that played lately and has no session now (ADR 0003, Amendment 4). A quiet card: the app's
 * icon and name, what it last played when this process saw it, and one primary action: Play when the app can be
 * resumed, else Open. The card is one node to assistive technology ("<app>, last played <time>") with the primary
 * action, Dismiss and Hide as actions; a tap on its header opens the app, and a swipe (or the Dismiss button) removes
 * it from the list.
 */
@Composable
internal fun MediaSourceCard(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    val name = content.appLabel ?: stringResource(R.string.stream_hidden_content)
    val resumable = StreamActionPolicy.offers(content, StreamAction.Play)
    val primary = if (resumable) StreamAction.Play else StreamAction.Open
    val primaryLabel =
        if (resumable) {
            stringResource(R.string.stream_media_source_play)
        } else {
            stringResource(R.string.stream_media_source_open, name)
        }
    val dismissLabel = stringResource(R.string.stream_notification_dismiss)
    val canDismiss = StreamActionPolicy.offers(content, StreamAction.Dismiss)
    val spoken = content.mediaSourceSpoken(env).joinToString()
    StreamCardSurface(content.id.value, content.app, content.icon, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        customActions =
                            mediaSourceActions(content, groupKey, callbacks, hideChoices, primaryLabel, dismissLabel)
                    }
                    .padding(start = RiffleSpacing.l, top = StreamHeaderTopPadding, bottom = RiffleSpacing.s),
        ) {
            StreamCardHeader(
                icon = { StreamIcon(content.icon, env, StreamHeaderIconSize, lightsCard = true) },
                title = name,
                subtitle = content.sourceSubtitle(env),
                modifier = Modifier.padding(end = RiffleSpacing.xs),
                contentModifier = Modifier.streamClickable({ callbacks.onAction(content, StreamAction.Open) }),
                trailing = { StreamHideMenuButton(hideChoices, content, groupKey, callbacks) },
            )
            // A gap under the header: the strip of this card behind another shows the header only, never the buttons.
            Spacer(modifier = Modifier.height(RiffleSpacing.s))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = RiffleSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalButton(
                    onClick = { callbacks.onAction(content, primary) },
                    modifier = Modifier.heightIn(min = StreamMinTarget),
                ) {
                    if (resumable) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canDismiss) {
                    TextButton(
                        onClick = { callbacks.onAction(content, StreamAction.Dismiss) },
                        modifier = Modifier.heightIn(min = StreamMinTarget),
                    ) { Text(dismissLabel) }
                }
            }
        }
    }
}

/** The actions of a recent player's one node: its primary action, Dismiss and the Hide choices. */
private fun mediaSourceActions(
    content: MediaContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
    hideChoices: List<HideChoice>,
    primaryLabel: String,
    dismissLabel: String,
): List<CustomAccessibilityAction> {
    val primary = if (StreamActionPolicy.offers(content, StreamAction.Play)) StreamAction.Play else StreamAction.Open

    fun act(
        label: String,
        action: () -> Unit,
    ) = CustomAccessibilityAction(label) {
        action()
        true
    }
    return buildList {
        add(act(primaryLabel) { callbacks.onAction(content, primary) })
        if (StreamActionPolicy.offers(content, StreamAction.Dismiss)) {
            add(act(dismissLabel) { callbacks.onAction(content, StreamAction.Dismiss) })
        }
        callbacks.onHide?.let { onHide ->
            hideChoices.forEach { choice -> add(act(choice.label) { onHide(content, groupKey, choice.kind) }) }
        }
    }
}

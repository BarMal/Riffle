package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.CardAction
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamContent

/**
 * The controls of an open card's actions in one row: the inline reply field and its send button, Dismiss, Snooze, the
 * transport, "Open in calendar", Complete. The same row is the Dock's actions row (the Dock draws it above its icons)
 * and, when the actions sit on the card, the card's own. Every control is a real focusable control with a 48 dp target
 * (the field is at least that tall) and a spoken label; none needs a gesture. [onToggleQueue] shows or hides the open
 * player's queue (it is no action on the content); everything else goes out through [callbacks].
 */
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun CardActionControls(
    content: StreamContent,
    actions: List<CardAction>,
    callbacks: StreamCallbacks,
    onToggleQueue: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val own = actions.filterIsInstance<CardAction.Own>()
    // The notification's own buttons are text and may be three long titles: they get a row of their own under the
    // controls (the reply field and the icons), so the field keeps its room on the narrowest card.
    Column(modifier = modifier.fillMaxWidth()) {
        val main = actions - own.toSet()
        if (main.isNotEmpty()) ActionRow(content, main, callbacks, onToggleQueue)
        if (own.isNotEmpty()) OwnActionRow(content, own, callbacks)
    }
}

/** The notification's own action buttons, in its order: each a text button with its title, a 48 dp target. */
@Composable
private fun OwnActionRow(
    content: StreamContent,
    actions: List<CardAction.Own>,
    callbacks: StreamCallbacks,
) {
    FitRow(modifier = Modifier.fillMaxWidth().testTag(CARD_OWN_ACTIONS_TEST_TAG)) {
        actions.forEach { action ->
            TextButton(
                onClick = { callbacks.onAction(content, StreamAction.OwnAction(action.id)) },
                modifier = Modifier.heightIn(min = StreamMinTarget).widthIn(min = StreamMinTarget),
                // Three titles share a 320 dp card: the button keeps its 48 dp height and gives up some side padding.
                contentPadding = PaddingValues(horizontal = RiffleSpacing.s),
            ) {
                Text(text = action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A row whose children each take the width their content wants, and, when together they want more than the row has,
 * only the widest give way (to an equal share, as far as it takes): a long title is ellipsized and short ones are not.
 */
@Composable
private fun FitRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = RiffleSpacing.xxs.roundToPx()
        val room = (constraints.maxWidth - gap * (measurables.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
        val wanted = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val widths = IntArray(measurables.size)
        // Water-filling: the narrowest take what they want while the rest still get at least an equal share of what is
        // left; whoever wants more than that takes the equal share.
        var left = room
        var remaining = measurables.size
        wanted.withIndex().sortedBy { it.value }.forEach { (index, want) ->
            val share = if (remaining > 0) left / remaining else 0
            widths[index] = minOf(want, share)
            left -= widths[index]
            remaining--
        }
        val placeables =
            measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(widths[index], widths[index], 0, constraints.maxHeight))
            }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

/** The tag of the row of a card's own action buttons (a test hook). */
internal const val CARD_OWN_ACTIONS_TEST_TAG = "stream-card-own-actions"

@Composable
@Suppress("CyclomaticComplexMethod")
private fun ActionRow(
    content: StreamContent,
    actions: List<CardAction>,
    callbacks: StreamCallbacks,
    onToggleQueue: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
    ) {
        actions.forEach { action ->
            when (action) {
                CardAction.Reply -> ReplyField(content, callbacks, Modifier.weight(1f))
                CardAction.Dismiss ->
                    ActionIcon(
                        Icons.Filled.Close,
                        stringResource(R.string.stream_notification_dismiss),
                    ) { callbacks.onAction(content, StreamAction.Dismiss) }
                CardAction.Snooze ->
                    ActionIcon(
                        StreamIcons.Snooze,
                        stringResource(R.string.stream_notification_snooze),
                    ) { callbacks.onAction(content, StreamAction.Snooze()) }
                CardAction.Previous ->
                    ActionIcon(
                        StreamIcons.SkipPrevious,
                        stringResource(R.string.stream_media_previous),
                    ) { callbacks.onAction(content, StreamAction.Previous) }
                is CardAction.PlayPause ->
                    ActionIcon(
                        if (action.playing) StreamIcons.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (action.playing) R.string.stream_media_pause else R.string.stream_media_play),
                    ) { callbacks.onAction(content, if (action.playing) StreamAction.Pause else StreamAction.Play) }
                CardAction.Next ->
                    ActionIcon(
                        StreamIcons.SkipNext,
                        stringResource(R.string.stream_media_next),
                    ) { callbacks.onAction(content, StreamAction.Next) }
                is CardAction.ToggleQueue ->
                    ActionIcon(
                        if (action.shown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        stringResource(
                            if (action.shown) R.string.stream_media_hide_queue else R.string.stream_media_show_queue,
                        ),
                    ) { onToggleQueue(!action.shown) }
                is CardAction.OpenEvent ->
                    StreamTextButton(
                        stringResource(
                            if (action.kind == EventKind.ALARM) {
                                R.string.stream_event_open_clock
                            } else {
                                R.string.stream_event_open_in_calendar
                            },
                        ),
                        { callbacks.onAction(content, StreamAction.Open) },
                    )
                CardAction.Complete ->
                    StreamTextButton(
                        stringResource(R.string.stream_task_complete_action),
                        { callbacks.onAction(content, StreamAction.Complete) },
                    )
                is CardAction.Own -> Unit
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(StreamMinTarget)) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

/** The inline reply: a field and its send button. The text is the user's, kept only while the card is open. */
@Composable
private fun ReplyField(
    content: StreamContent,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    var reply by remember(content.id) { mutableStateOf("") }
    val send = {
        if (reply.isNotBlank()) {
            callbacks.onAction(content, StreamAction.Reply(reply))
            reply = ""
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = reply,
            onValueChange = { reply = it },
            modifier = Modifier.weight(1f).heightIn(min = StreamMinTarget),
            placeholder = { Text(stringResource(R.string.stream_notification_reply_hint), maxLines = 1) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        ActionIcon(
            Icons.AutoMirrored.Filled.Send,
            stringResource(R.string.stream_notification_send),
        ) { send() }
    }
}

/**
 * The same controls as accessibility actions of the open card itself (the Dock's row is reachable by traversal, but a
 * screen reader focused on the card must not have to leave it): every control that acts on the content, by its spoken
 * label. Reply is the field in the row (it needs text), so it is not repeated here.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun Modifier.cardActionAccessibility(
    content: StreamContent,
    actions: List<CardAction>,
    callbacks: StreamCallbacks,
    onToggleQueue: (Boolean) -> Unit,
    /** More actions of the card that are no action on its content (Previous and Next card). */
    extraActions: List<CustomAccessibilityAction> = emptyList(),
): Modifier {
    val dismiss = stringResource(R.string.stream_notification_dismiss)
    val snooze = stringResource(R.string.stream_notification_snooze)
    val previous = stringResource(R.string.stream_media_previous)
    val next = stringResource(R.string.stream_media_next)
    val play = stringResource(R.string.stream_media_play)
    val pause = stringResource(R.string.stream_media_pause)
    val showQueue = stringResource(R.string.stream_media_show_queue)
    val hideQueue = stringResource(R.string.stream_media_hide_queue)
    val calendar = stringResource(R.string.stream_event_open_in_calendar)
    val clock = stringResource(R.string.stream_event_open_clock)
    val complete = stringResource(R.string.stream_task_complete_action)
    val labelled =
        actions.mapNotNull { action ->
            val label =
                when (action) {
                    CardAction.Reply -> null
                    CardAction.Dismiss -> dismiss
                    CardAction.Snooze -> snooze
                    CardAction.Previous -> previous
                    is CardAction.PlayPause -> if (action.playing) pause else play
                    CardAction.Next -> next
                    is CardAction.ToggleQueue -> if (action.shown) hideQueue else showQueue
                    is CardAction.OpenEvent -> if (action.kind == EventKind.ALARM) clock else calendar
                    CardAction.Complete -> complete
                    is CardAction.Own -> action.title
                }
            label?.let { action to it }
        }
    return semantics {
        customActions =
            labelled.map { (action, label) ->
                CustomAccessibilityAction(label) {
                    val streamAction = StreamCardActions.streamActionFor(action)
                    if (streamAction != null) {
                        callbacks.onAction(content, streamAction)
                    } else if (action is CardAction.ToggleQueue) {
                        onToggleQueue(!action.shown)
                    }
                    true
                }
            } + extraActions
    }
}

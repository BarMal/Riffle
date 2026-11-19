package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.ActionRowFit
import com.riffle.core.domain.launcher.stream.CardAction
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent

/** The test tags of the player's actions row: the transport group and the secondary actions. */
internal const val MEDIA_TRANSPORT_TEST_TAG = "media-actions-transport"
internal const val MEDIA_SECONDARY_TEST_TAG = "media-actions-secondary"

private val PrimarySize = 56.dp
private val TransportGap = RiffleSpacing.s

private const val CENTER = "center"
private const val START = "start"
private const val END = "end"

private fun CardAction.isTransport() =
    this == CardAction.Previous || this is CardAction.PlayPause || this == CardAction.Next

/**
 * The open player's actions as one deliberate row, on the Dock's glass or on the card: previous, play or pause and next
 * as a group centred on the row (48 dp targets at an even gap, play and pause larger and tonal, as the primary), with
 * the secondary actions at the two ends, "Open player" first, when they fit beside it ([ActionRowFit.centredFits]).
 * When they do not (a compact width), the transport keeps the first row and the secondary actions take a second, spread
 * evenly instead of packed to the left. The row's padding and the 48 dp targets are the quick actions' own; no media
 * special case of spacing.
 */
@Composable
internal fun MediaActionRow(
    content: StreamContent,
    actions: List<CardAction>,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val transport = actions.filter { it.isTransport() }
    val secondary = actions.filterNot { it.isTransport() }
    val start = secondary.filterIsInstance<CardAction.OpenContent>()
    val end = secondary - start.toSet()
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Row(
                modifier = Modifier.layoutId(CENTER).testTag(MEDIA_TRANSPORT_TEST_TAG),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TransportGap),
            ) { transport.forEach { TransportAction(content, it, callbacks) } }
            Row(
                modifier = Modifier.layoutId(START).testTag(MEDIA_SECONDARY_TEST_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) { start.forEach { SecondaryAction(content, it, callbacks) } }
            Row(
                modifier = Modifier.layoutId(END),
                verticalAlignment = Alignment.CenterVertically,
            ) { end.forEach { SecondaryAction(content, it, callbacks) } }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = RiffleSpacing.xxs.roundToPx()

        fun natural(id: String) = measurables.first { it.layoutId == id }.maxIntrinsicWidth(constraints.maxHeight)
        val widths = listOf(START, CENTER, END).map { natural(it).coerceAtMost(width) }
        val oneRow = ActionRowFit.centredFits(widths[0], widths[1], widths[2], width, gap)
        val placeables =
            listOf(START, CENTER, END).mapIndexed { index, id ->
                measurables.first { it.layoutId == id }
                    .measure(Constraints(minWidth = widths[index], maxWidth = widths[index]))
            }
        val (startP, centerP, endP) = placeables
        if (oneRow) {
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                startP.placeRelative(0, (height - startP.height) / 2)
                centerP.placeRelative((width - centerP.width) / 2, (height - centerP.height) / 2)
                endP.placeRelative(width - endP.width, (height - endP.height) / 2)
            }
        } else {
            val second = listOf(startP, endP).filter { it.width > 0 }
            val xs = ActionRowFit.evenlySpread(second.map { it.width }, width)
            val firstHeight = centerP.height
            val secondHeight = second.maxOfOrNull { it.height } ?: 0
            layout(width, firstHeight + secondHeight) {
                centerP.placeRelative((width - centerP.width) / 2, 0)
                second.forEachIndexed { i, p -> p.placeRelative(xs[i], firstHeight + (secondHeight - p.height) / 2) }
            }
        }
    }
}

@Composable
private fun TransportAction(
    content: StreamContent,
    action: CardAction,
    callbacks: StreamCallbacks,
) {
    when (action) {
        CardAction.Previous ->
            IconButton(onClick = {
                callbacks.onAction(content, StreamAction.Previous)
            }, modifier = Modifier.size(StreamMinTarget)) {
                Icon(StreamIcons.SkipPrevious, stringResource(R.string.stream_media_previous), Modifier.size(24.dp))
            }
        is CardAction.PlayPause -> {
            val label = stringResource(if (action.playing) R.string.stream_media_pause else R.string.stream_media_play)
            val toggle = if (action.playing) StreamAction.Pause else StreamAction.Play
            FilledTonalIconButton(
                onClick = { callbacks.onAction(content, toggle) },
                modifier = Modifier.size(PrimarySize),
            ) {
                Icon(
                    if (action.playing) StreamIcons.Pause else Icons.Filled.PlayArrow,
                    label,
                    Modifier.size(28.dp),
                )
            }
        }
        CardAction.Next ->
            IconButton(
                onClick = { callbacks.onAction(content, StreamAction.Next) },
                modifier = Modifier.size(StreamMinTarget),
            ) {
                Icon(StreamIcons.SkipNext, stringResource(R.string.stream_media_next), Modifier.size(24.dp))
            }
        else -> Unit
    }
}

@Composable
private fun SecondaryAction(
    content: StreamContent,
    action: CardAction,
    callbacks: StreamCallbacks,
) {
    when (action) {
        is CardAction.OpenContent ->
            StreamTextButton(
                stringResource(if (action.player) R.string.stream_media_open_player else R.string.stream_open),
                { callbacks.onAction(content, StreamAction.Open) },
            )
        else -> Unit
    }
}

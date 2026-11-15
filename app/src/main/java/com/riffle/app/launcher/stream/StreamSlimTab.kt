package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.CountRecomposition
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.SlimTabs
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent

/** The test tag of a slim tab's one line of title. */
internal const val STREAM_SLIM_TAB_TITLE_TAG = "stream-slim-tab-title"

/** The test tag of the icon or avatar a slim tab leads with. */
internal const val STREAM_SLIM_TAB_ICON_TAG = "stream-slim-tab-icon"

/**
 * A neighbour of an open card as a slim tab ("Slim neighbour strips"): the card's own glass (so a tab is the card,
 * drawn only as far as its header), holding one line, a small icon or avatar and the title in a small label style.
 * No subtitle, no time, no body. It fills the slot it is given ([SlimTabs.PITCH_DP] tall once the card is open), so the
 * glass, its rounded top and the cut that tucks it under the open card are the card's. Decorative for assistive
 * technology: the slot it is in is the one button, labelled with the title. [tuck] is how far the glass goes on under
 * the slot (the card in front covers it), so the tab's own rounded foot is never drawn against that card.
 */
@Composable
internal fun SlimTab(
    entry: StackEntry,
    env: StreamEnvironment,
    modifier: Modifier = Modifier,
    tuck: Dp = 0.dp,
) {
    CountRecomposition("slim-tab:${entry.id}")
    val title = entry.peekText(env)
    val content = (entry as? ContentEntry)?.content
    StreamCardSurface(
        key = "${entry.id}:tab",
        app = content?.appRef(),
        icon = content?.iconHandle(),
        env = env,
        modifier = if (tuck > 0.dp) modifier.tuckedUnder(tuck) else modifier,
    ) { sourceModifier ->
        Row(
            modifier =
                sourceModifier.aboveTuck(tuck).fillMaxSize().padding(horizontal = RiffleSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            SlimTabIcon(entry, content, env)
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag(STREAM_SLIM_TAB_TITLE_TAG),
            )
        }
    }
}

/** The icon a tab leads with, [SlimTabs.ICON_DP] across: the person's avatar, the app's icon or a kind's glyph. */
@Composable
private fun SlimTabIcon(
    entry: StackEntry,
    content: StreamContent?,
    env: StreamEnvironment,
) {
    val size = SlimTabs.ICON_DP.dp
    Box(modifier = Modifier.size(size).testTag(STREAM_SLIM_TAB_ICON_TAG), contentAlignment = Alignment.Center) {
        when {
            content is NotificationContent ->
                StreamAvatar(content.avatar, content.icon, content.headline, env, size, person = content.hasPerson)
            content?.iconHandle() != null ->
                StreamIcon(content.iconHandle(), env, size, shape = RiffleShapes.small, lightsCard = true)
            else -> StreamGlyph(slimTabGlyph(entry, content), Modifier.size(size))
        }
    }
}

private fun slimTabGlyph(
    entry: StackEntry,
    content: StreamContent?,
) = when {
    content is EventContent || entry is TomorrowEntry -> StreamIcons.Agenda
    content is ArticleContent -> StreamIcons.Feeds
    content is TaskContent || entry is ToDoEntry -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Info
}

/**
 * How far a slim tab's glass is drawn past its slot, down under the open card: the card's rounded top corners leave a
 * little of the strip behind them showing, as they do on a card's strip, and the cut ([stripCut]) hides the rest.
 */
internal val SlimTabTuck = 24.dp

/** This node as tall as the room it is given and [tuck] more, drawn past it (the layout is not taller). */
private fun Modifier.tuckedUnder(tuck: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable =
            measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + tuck.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/** The part of a tucked tab that shows: as tall as the tab is less [tuck], at its top. */
private fun Modifier.aboveTuck(tuck: Dp): Modifier =
    if (tuck <= 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val height = (constraints.maxHeight - tuck.roundToPx()).coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
        }
    }

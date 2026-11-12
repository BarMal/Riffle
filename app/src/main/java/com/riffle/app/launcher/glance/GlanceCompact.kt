package com.riffle.app.launcher.glance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.stream.StreamCardSurface
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamIcon
import com.riffle.app.launcher.stream.StreamIcons
import com.riffle.app.launcher.stream.StreamMinTarget
import com.riffle.app.launcher.stream.StreamTextButton
import com.riffle.app.launcher.stream.TransportButton
import com.riffle.app.launcher.stream.articleMeta
import com.riffle.app.launcher.stream.eventCountdownText
import com.riffle.app.launcher.stream.eventSpanText
import com.riffle.app.launcher.stream.eventTitle
import com.riffle.app.launcher.stream.hiddenText
import com.riffle.app.launcher.stream.streamClickable
import com.riffle.core.domain.launcher.glance.GlanceLayout
import com.riffle.core.domain.launcher.glance.GlanceState
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy

private val CompactThumb: Dp = RiffleSpacing.xxxl
private val MinimalThumb: Dp = RiffleSpacing.xxl

/**
 * Now playing in less room than the Stream's card needs: the artwork, the title and artist, and (when the widget is
 * wide enough, [GlanceLayout.COMPACT]) previous, play or pause and next as three 48 dp buttons. At
 * [GlanceLayout.MINIMAL] the controls are in the menu and in accessibility actions. The text opens the Stream.
 */
@Composable
internal fun CompactMedia(
    content: MediaContent,
    layout: GlanceLayout,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
    actions: List<GlanceAction>,
) {
    GlanceRow(content.id.value, content.app, content.icon, env) {
        StreamIcon(
            content.artwork ?: content.icon,
            env,
            if (layout == GlanceLayout.MINIMAL) MinimalThumb else CompactThumb,
        )
        GlanceText(
            title = content.title ?: hiddenText(),
            subtitle = content.artist?.takeIf { it.isNotBlank() },
            modifier =
                Modifier.weight(
                    1f,
                ).streamClickable(callbacks.openStream, stringResource(R.string.glance_open_stream)),
        )
        if (layout.showsFullTransport) {
            val playing = content.isPlaying == true
            val toggle = if (playing) StreamAction.Pause else StreamAction.Play
            TransportButton(
                StreamIcons.SkipPrevious,
                stringResource(R.string.stream_media_previous),
                StreamActionPolicy.offers(content, StreamAction.Previous),
            ) { callbacks.perform(content, StreamAction.Previous) }
            TransportButton(
                if (playing) StreamIcons.Pause else Icons.Filled.PlayArrow,
                stringResource(if (playing) R.string.stream_media_pause else R.string.stream_media_play),
                StreamActionPolicy.offers(content, toggle),
            ) { callbacks.perform(content, toggle) }
            TransportButton(
                StreamIcons.SkipNext,
                stringResource(R.string.stream_media_next),
                StreamActionPolicy.offers(content, StreamAction.Next),
            ) { callbacks.perform(content, StreamAction.Next) }
        } else {
            GlanceMenuButton(actions)
        }
    }
}

/** The next event in less room than its card: title, time span and countdown (and the place when there are rows). */
@Composable
internal fun CompactEvent(
    content: EventContent,
    layout: GlanceLayout,
    span: GridSpan,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
    actions: List<GlanceAction>,
) {
    val place = (content.location ?: content.calendarName)?.takeIf { it.isNotBlank() && span.rows >= 2 }
    val timing = eventSpanText(content, env) + ", " + eventCountdownText(content, env.nowEpochMillis)
    GlanceRow(content.id.value, null, null, env) {
        GlanceText(
            title = eventTitle(content),
            subtitle = timing,
            detail = place,
            modifier =
                Modifier.weight(1f).then(
                    if (StreamActionPolicy.offers(content, StreamAction.Open)) {
                        Modifier.streamClickable(
                            { callbacks.perform(content, StreamAction.Open) },
                            stringResource(R.string.glance_open_event),
                        )
                    } else {
                        Modifier
                    },
                ),
            emphasizeSubtitle = layout == GlanceLayout.COMPACT,
        )
        GlanceMenuButton(actions)
    }
}

/** The top story in less room than its card: a thumbnail, the headline and the feed with its age. */
@Composable
internal fun CompactArticle(
    content: ArticleContent,
    layout: GlanceLayout,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
    actions: List<GlanceAction>,
) {
    GlanceRow(content.id.value, null, null, env) {
        content.image?.let { StreamIcon(it, env, if (layout == GlanceLayout.MINIMAL) MinimalThumb else CompactThumb) }
        GlanceText(
            title = content.title ?: hiddenText(),
            subtitle = articleMeta(content, env).takeIf { it.isNotBlank() },
            titleLines = 2,
            modifier =
                Modifier.weight(1f).then(
                    if (StreamActionPolicy.offers(content, StreamAction.Open)) {
                        Modifier.streamClickable(
                            { callbacks.perform(content, StreamAction.Open) },
                            stringResource(R.string.glance_open_article),
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        GlanceMenuButton(actions)
    }
}

/** The glass and row every compact layout shares: the Stream's own card surface, lit by the content's app colour. */
@Composable
private fun GlanceRow(
    key: String,
    app: com.riffle.core.domain.launcher.stream.StreamAppRef?,
    icon: com.riffle.core.domain.launcher.workspace.ItemImageHandle?,
    env: StreamEnvironment,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    StreamCardSurface(key, app, icon, env, Modifier.fillMaxSize()) { sourceModifier ->
        Row(
            modifier = sourceModifier.fillMaxSize().padding(horizontal = RiffleSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            content = content,
        )
    }
}

@Composable
private fun GlanceText(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    titleLines: Int = 1,
    emphasizeSubtitle: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxHeight().padding(vertical = RiffleSpacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style =
                    if (emphasizeSubtitle) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A 48 dp "More actions" button whose menu holds [actions]: the equivalent of controls the span has no room for. */
@Composable
internal fun GlanceMenuButton(
    actions: List<GlanceAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(StreamMinTarget)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.glance_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.run()
                    },
                )
            }
        }
    }
}

/** Loading, empty, permission, off or unavailable: the Stream's own card surface and one honest sentence. */
@Composable
internal fun GlanceStateCard(
    kind: GlanceWidgetKind,
    state: GlanceState,
    layout: GlanceLayout,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
) {
    val message =
        when (state) {
            GlanceState.Empty -> stringResource(kind.emptyRes())
            GlanceState.Loading -> stringResource(R.string.stream_loading)
            is GlanceState.PermissionRequired -> stringResource(R.string.stream_permission_message)
            GlanceState.Off -> stringResource(R.string.stream_off_message)
            GlanceState.Unavailable -> stringResource(R.string.stream_unavailable)
            is GlanceState.Showing -> ""
        }
    val button: Pair<String, () -> Unit>? =
        when (state) {
            is GlanceState.PermissionRequired ->
                stringResource(R.string.stream_permission_action) to { callbacks.requestAccess(state.sources) }
            GlanceState.Off -> stringResource(R.string.stream_off_action) to callbacks.enable
            else -> null
        }
    StreamCardSurface("glance:${kind.storedId}", null, null, env, Modifier.fillMaxSize()) { sourceModifier ->
        Row(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .streamClickable(callbacks.openStream, stringResource(R.string.glance_open_stream))
                    .padding(horizontal = RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style =
                    if (layout == GlanceLayout.FULL) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            button?.let { (label, run) -> StreamTextButton(label, run) }
        }
    }
}

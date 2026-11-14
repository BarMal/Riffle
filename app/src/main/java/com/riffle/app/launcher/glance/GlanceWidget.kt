package com.riffle.app.launcher.glance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.stream.ArticleCard
import com.riffle.app.launcher.stream.EventCard
import com.riffle.app.launcher.stream.MediaCard
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.articleMeta
import com.riffle.app.launcher.stream.eventCountdownText
import com.riffle.app.launcher.stream.eventSpanText
import com.riffle.app.launcher.stream.eventTitle
import com.riffle.app.launcher.stream.hiddenText
import com.riffle.app.launcher.stream.withLiveClock
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.glance.GlanceLayout
import com.riffle.core.domain.launcher.glance.GlanceState
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What a glance widget asks of its host. [perform] is the Stream's one action gateway, [openStream] lands the Stream
 * on the widget's section, [requestAccess] is the Stream's own explicit access flow and [enable] turns the section's
 * sources back on (only ever from a visible button).
 */
internal class GlanceCallbacks(
    val perform: (StreamContent, StreamAction) -> Unit = { _, _ -> },
    val openStream: () -> Unit = {},
    val requestAccess: (List<SourceId>) -> Unit = {},
    val enable: () -> Unit = {},
)

/** The callbacks the Stream's own cards take: the player title opens the Stream here, not the player. */
internal fun GlanceCallbacks.forCards(): StreamCallbacks =
    StreamCallbacks(
        onAction = { content, action ->
            if (content is MediaContent && action == StreamAction.Open) openStream() else perform(content, action)
        },
    )

/**
 * A glance widget on Home: reads its section through the shell's [GlanceHost] while it is composed and draws
 * [GlanceWidgetBody]. [interactive] is false while a drag is in flight, so letting go of a long press never also
 * presses the control under the finger.
 */
@Composable
internal fun GlanceWidget(
    kind: GlanceWidgetKind,
    span: GridSpan,
    interactive: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val host = LocalGlanceHost.current
    val state = rememberGlanceState(host, kind)
    val scope = rememberCoroutineScope()
    val env = remember(host) { host?.environment() }?.withLiveClock()
    val callbacks =
        remember(host, kind, interactive) {
            GlanceCallbacks(
                perform = { content, action ->
                    if (interactive() && host != null) {
                        scope.launch(Dispatchers.Default) {
                            runCatching { host.stream.actions.perform(content, action) }
                        }
                    }
                },
                openStream = { if (interactive()) host?.openStream?.invoke(kind.focusTarget()) },
                requestAccess = { sources ->
                    if (interactive()) {
                        sources.firstOrNull()?.let { host?.stream?.requestAccess?.invoke(it) }
                    }
                },
                enable = {
                    if (interactive()) {
                        host?.stream?.enableSources?.invoke(
                            kind.section.itemSources.map(StreamSourceIds::userSourceOf).distinct(),
                        )
                    }
                },
            )
        }
    if (env == null) {
        GlassSurface(
            modifier = modifier,
            weight = GlassWeight.Regular,
            shape = LocalLauncherCardShape.current,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(kind.labelRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        GlanceWidgetBody(kind, state, span, env, callbacks, modifier)
    }
}

/**
 * The widget, stateless: [state] in, the Stream's own card (or a compact layout of the same content) out. It is one
 * labelled group; every control is also in its single list of accessibility actions.
 */
@Composable
internal fun GlanceWidgetBody(
    kind: GlanceWidgetKind,
    state: GlanceState,
    span: GridSpan,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
    modifier: Modifier = Modifier,
) {
    val layout = kind.layoutFor(span)
    val actions = glanceActions(state, callbacks)
    val label = stringResource(kind.labelRes())
    val summary = glanceSummary(kind, state, env)
    val group = stringResource(R.string.glance_group_description, label, summary)
    Box(
        modifier =
            modifier.semantics {
                isTraversalGroup = true
                contentDescription = group
                customActions =
                    actions.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.run()
                            true
                        }
                    }
            },
    ) {
        when (state) {
            is GlanceState.Showing -> GlanceShowing(state.content, layout, span, env, callbacks, actions)
            else -> GlanceStateCard(kind, state, layout, env, callbacks)
        }
    }
}

@Composable
private fun GlanceShowing(
    content: StreamContent,
    layout: GlanceLayout,
    span: GridSpan,
    env: StreamEnvironment,
    callbacks: GlanceCallbacks,
    actions: List<GlanceAction>,
) {
    if (layout == GlanceLayout.FULL) {
        val cardCallbacks = callbacks.forCards()
        Box(Modifier.fillMaxSize()) {
            when (content) {
                // The Stream's card, without its queue toggle: there is no room to expand a widget.
                is MediaContent ->
                    MediaCard(content.copy(queue = emptyList()), env, cardCallbacks, null, Modifier.fillMaxSize())
                is EventContent -> EventCard(content, env, cardCallbacks, null, Modifier.fillMaxSize())
                is ArticleContent -> ArticleCard(content, env, cardCallbacks, null, Modifier.fillMaxSize())
                else -> Unit
            }
            GlanceMenuButton(actions, Modifier.align(Alignment.TopEnd))
        }
    } else {
        when (content) {
            is MediaContent -> CompactMedia(content, layout, env, callbacks, actions)
            is EventContent -> CompactEvent(content, layout, span, env, callbacks, actions)
            is ArticleContent -> CompactArticle(content, layout, env, callbacks, actions)
            else -> Unit
        }
    }
}

/** One thing a widget's controls can do: a menu item and an accessibility action with the same label. */
internal class GlanceAction(val label: String, val run: () -> Unit)

@Composable
internal fun glanceActions(
    state: GlanceState,
    callbacks: GlanceCallbacks,
): List<GlanceAction> {
    val openStream = GlanceAction(stringResource(R.string.glance_open_stream), callbacks.openStream)
    val content = (state as? GlanceState.Showing)?.content
    return when (content) {
        is MediaContent -> mediaActions(content, callbacks) + openStream
        is EventContent ->
            listOfNotNull(
                GlanceAction(
                    stringResource(R.string.glance_open_event),
                ) { callbacks.perform(content, StreamAction.Open) }
                    .takeIf { StreamActionPolicy.offers(content, StreamAction.Open) },
                openStream,
            )
        is ArticleContent -> articleActions(content, callbacks) + openStream
        else -> {
            val extra =
                when (state) {
                    is GlanceState.PermissionRequired ->
                        GlanceAction(
                            stringResource(R.string.stream_permission_action),
                        ) { callbacks.requestAccess(state.sources) }
                    GlanceState.Off -> GlanceAction(stringResource(R.string.stream_off_action), callbacks.enable)
                    else -> null
                }
            listOfNotNull(extra, openStream)
        }
    }
}

@Composable
private fun mediaActions(
    content: MediaContent,
    callbacks: GlanceCallbacks,
): List<GlanceAction> {
    val policy = StreamActionPolicy
    val toggle = if (content.isPlaying == true) StreamAction.Pause else StreamAction.Play
    val toggleLabel =
        stringResource(if (content.isPlaying == true) R.string.stream_media_pause else R.string.stream_media_play)
    return listOfNotNull(
        GlanceAction(
            stringResource(R.string.stream_media_previous),
        ) { callbacks.perform(content, StreamAction.Previous) }
            .takeIf { policy.offers(content, StreamAction.Previous) },
        GlanceAction(toggleLabel) { callbacks.perform(content, toggle) }.takeIf { policy.offers(content, toggle) },
        GlanceAction(stringResource(R.string.stream_media_next)) { callbacks.perform(content, StreamAction.Next) }
            .takeIf { policy.offers(content, StreamAction.Next) },
    )
}

@Composable
private fun articleActions(
    content: ArticleContent,
    callbacks: GlanceCallbacks,
): List<GlanceAction> {
    val policy = StreamActionPolicy
    return listOfNotNull(
        GlanceAction(stringResource(R.string.glance_open_article)) { callbacks.perform(content, StreamAction.Open) }
            .takeIf { policy.offers(content, StreamAction.Open) },
        GlanceAction(
            stringResource(R.string.stream_article_mark_read),
        ) { callbacks.perform(content, StreamAction.MarkRead) }
            .takeIf { policy.offers(content, StreamAction.MarkRead) },
    )
}

/** The one sentence a screen reader gives for the widget: what it shows, or why it shows nothing. */
@Composable
internal fun glanceSummary(
    kind: GlanceWidgetKind,
    state: GlanceState,
    env: StreamEnvironment,
): String =
    when (state) {
        is GlanceState.Showing -> contentSummary(state.content, env)
        GlanceState.Loading -> stringResource(R.string.stream_loading)
        GlanceState.Empty -> stringResource(kind.emptyRes())
        is GlanceState.PermissionRequired -> stringResource(R.string.stream_permission_message)
        GlanceState.Off -> stringResource(R.string.stream_off_message)
        GlanceState.Unavailable -> stringResource(R.string.stream_unavailable)
    }

@Composable
private fun contentSummary(
    content: StreamContent,
    env: StreamEnvironment,
): String =
    when (content) {
        is MediaContent -> mediaSummary(content)
        is EventContent ->
            listOf(eventTitle(content), eventSpanText(content, env), eventCountdownText(content, env.nowEpochMillis))
                .joinToString(", ")
        is ArticleContent ->
            listOfNotNull(content.title ?: hiddenText(), articleMeta(content, env).takeIf { it.isNotBlank() })
                .joinToString(", ")
        else -> ""
    }

@Composable
private fun mediaSummary(content: MediaContent): String {
    val title = content.title ?: hiddenText()
    val artist = content.artist?.takeIf { it.isNotBlank() } ?: return title
    return stringResource(R.string.glance_by_artist, title, artist)
}

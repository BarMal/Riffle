package com.riffle.app.launcher.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glassSpotlightSource
import com.riffle.app.launcher.rememberGlassSpotlightColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent

private val TileWidth = 88.dp
private val TileIconSize = RiffleSpacing.xxxl

/**
 * An app as a tile: its icon and name on regular glass lit by the app's own colour. Apps have no card (ADR 0003), so
 * Recents' Stack view is a row of these. A tap launches the app; long-press (or the accessibility actions) offers Hide.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentAppTile(
    content: RecentAppContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    var menuOpen by remember { mutableStateOf(false) }
    val identity = remember(content, env.installedApps) { env.identityFor(content.app, content.icon) }
    Box(modifier = modifier) {
        GlassSurface(
            modifier = Modifier.width(TileWidth),
            weight = GlassWeight.Regular,
            shape = LocalLauncherCardShape.current,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            spotlight = identity != null,
        ) {
            val color = rememberGlassSpotlightColor(identity = identity, appIconLoader = env.appIconLoader)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .glassSpotlightSource(key = content.id.value, color = color)
                        .heightIn(min = StreamMinTarget)
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.stream_hide),
                            onLongClick = if (hideChoices.isEmpty()) null else ({ menuOpen = true }),
                            onClick = { callbacks.onAction(content, StreamAction.Open) },
                        )
                        .streamHideActions(hideChoices, content, groupKey, callbacks)
                        .padding(RiffleSpacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            ) {
                StreamIcon(content.icon, env, TileIconSize)
                Text(
                    text = content.label ?: stringResource(R.string.stream_hidden_content),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val onHide = callbacks.onHide
        if (onHide != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                hideChoices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = {
                            menuOpen = false
                            onHide(content, groupKey, choice.kind)
                        },
                    )
                }
            }
        }
    }
}

/** Recents' Stack view: one scrolling row of app tiles. */
@Composable
internal fun RecentAppsRow(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = RiffleSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        items.filterIsInstance<RecentAppContent>().forEach { app ->
            RecentAppTile(app, env, callbacks, groupKey)
        }
    }
}

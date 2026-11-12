@file:Suppress("LongParameterList")

package com.riffle.app.launcher.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.ShowAsMenuItems
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSpine
import com.riffle.core.domain.launcher.stream.StreamSpineMarker
import com.riffle.core.domain.launcher.stream.StreamWideView

internal const val STREAM_SPINE_TEST_TAG = "stream-spine"
internal const val STREAM_OPTIONS_TEST_TAG = "stream-options"

internal fun streamSpineMarkerTestTag(pageKey: String): String = "stream-spine-marker-$pageKey"

private val MarkerWidth = StreamMinTarget
private val MarkerIconSize = RiffleSpacing.xl
private val SeparatorWidth = 1.dp

/** The strings and state the Stream's own menu needs: Show as (wide postures only) and the hidden sections. */
internal class StreamMenuModel(
    val wideView: StreamWideView?,
    val hiddenSections: List<StreamSection>,
) {
    val isEmpty: Boolean get() = wideView == null && hiddenSections.isEmpty()
}

/**
 * The Stream's page indicator (ADR 0003, Views), after Calm's spine: a Thin-glass strip at the top of the Stream, above
 * the page's header, with one marker per page (the app's icon or the section's glyph). The current marker is
 * emphasised and the strip scrolls with the pager, following a swipe in flight; a tap jumps to that page. A tab row for
 * assistive technology (each marker is a selected or unselected tab named for its page). Long-press, or the options
 * button, opens the Stream's own menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamSpineBar(
    pages: List<StreamPage>,
    currentIndex: Int,
    /** The fractional page index while a swipe is in flight (see [StreamSpine.focusIndex]). */
    focusIndex: Float,
    env: StreamEnvironment,
    menu: StreamMenuModel,
    onSelect: (Int) -> Unit,
    onMenuAction: (StreamMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val markers = StreamSpine.markers(pages, currentIndex)
    val title = stringResource(R.string.stream_title)
    Box(modifier = modifier.fillMaxWidth()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpineScroller(
                    markers = markers,
                    pages = pages,
                    focusIndex = focusIndex,
                    env = env,
                    title = title,
                    onSelect = onSelect,
                    onLongPress = { menuOpen = !menu.isEmpty },
                    modifier = Modifier.weight(1f),
                )
                if (!menu.isEmpty) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(StreamMinTarget).testTag(STREAM_OPTIONS_TEST_TAG),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.stream_section_options, title),
                        )
                    }
                }
            }
        }
        StreamMenu(menu, menuOpen, { menuOpen = false }, onMenuAction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpineScroller(
    markers: List<StreamSpineMarker>,
    pages: List<StreamPage>,
    focusIndex: Float,
    env: StreamEnvironment,
    title: String,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.height(StreamMinTarget)) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val markerPx = with(density) { MarkerWidth.toPx() }
        val separatorPx = with(density) { (SeparatorWidth + RiffleSpacing.xxs * 2).toPx() }
        val lefts =
            remember(markers, markerPx, separatorPx) {
                var x = 0f
                markers.map { marker ->
                    if (marker.startsSection) x += separatorPx
                    x.also { x += markerPx }
                }
            }
        // Centre the marker at the focus index, the way Calm's carousel follows its pager; the strip is never scrolled
        // past its own ends, so the first and last markers rest against the edges.
        val target =
            if (lefts.isEmpty()) {
                0
            } else {
                val low = focusIndex.toInt().coerceIn(0, lefts.lastIndex)
                val high = (low + 1).coerceAtMost(lefts.lastIndex)
                val fraction = (focusIndex - low).coerceIn(0f, 1f)
                (lefts[low] + (lefts[high] - lefts[low]) * fraction + markerPx / 2f - viewportPx / 2f).toInt()
            }
        LaunchedEffect(target, markers.size) { scroll.scrollTo(target.coerceIn(0, scroll.maxValue)) }
        Row(
            modifier =
                Modifier
                    .horizontalScroll(scroll)
                    .selectableGroup()
                    .semantics { contentDescription = title }
                    .testTag(STREAM_SPINE_TEST_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            markers.forEachIndexed { index, marker ->
                if (marker.startsSection) {
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = RiffleSpacing.xxs)
                                .width(SeparatorWidth)
                                .height(RiffleSpacing.xl)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                SpineMarker(marker, pages[index], env, { onSelect(index) }, onLongPress)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpineMarker(
    marker: StreamSpineMarker,
    page: StreamPage,
    env: StreamEnvironment,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val name = page.title()
    val description = stringResource(R.string.stream_spine_marker, name, marker.position, marker.total)
    val fill by animateColorAsState(
        targetValue = if (marker.selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "stream-spine-marker",
    )
    Box(
        modifier =
            Modifier
                .size(MarkerWidth)
                .combinedClickable(
                    role = Role.Tab,
                    onClickLabel = description,
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .semantics {
                    contentDescription = description
                    selected = marker.selected
                }
                .testTag(streamSpineMarkerTestTag(marker.pageKey))
                .padding(RiffleSpacing.xs)
                .background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        StreamPageGlyph(page, env, MarkerIconSize)
    }
}

/** What the Stream's own menu can ask for; the surface turns each into a layout action and the Undo it offers. */
internal sealed interface StreamMenuAction {
    data class SetWideView(val view: StreamWideView) : StreamMenuAction

    data class ShowSection(val section: StreamSection) : StreamMenuAction
}

internal fun StreamWideView.titleRes(): Int =
    when (this) {
        StreamWideView.PAGES -> R.string.stream_wide_pages
        StreamWideView.STAGE -> R.string.stream_wide_stage
    }

/** The Stream-level menu: Show as Pages or Stage (wide postures), and a way back for every hidden section. */
@Composable
internal fun StreamMenu(
    menu: StreamMenuModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (StreamMenuAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menu.wideView?.let { current ->
            ShowAsMenuItems(
                options = StreamWideView.entries,
                current = current,
                optionLabel = { option -> stringResource(option.titleRes()) },
                onSelect = { option ->
                    onDismiss()
                    onAction(StreamMenuAction.SetWideView(option))
                },
            )
        }
        if (menu.wideView != null && menu.hiddenSections.isNotEmpty()) HorizontalDivider()
        menu.hiddenSections.forEach { section ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.stream_show_hidden_section, stringResource(section.titleRes())),
                    )
                },
                onClick = {
                    onDismiss()
                    onAction(StreamMenuAction.ShowSection(section))
                },
            )
        }
    }
}

internal fun StreamMenuAction.toLayoutAction(): StreamLayoutAction =
    when (this) {
        is StreamMenuAction.SetWideView -> StreamLayoutAction.SetWideView(view)
        is StreamMenuAction.ShowSection -> StreamLayoutAction.SetEnabled(section, true)
    }

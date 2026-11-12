@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.ShowAsMenuItems
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.TaskContent
import java.time.LocalDate

@StringRes
internal fun StreamSection.titleRes(): Int =
    when (this) {
        StreamSection.NOW -> R.string.stream_section_now
        StreamSection.NOTIFICATIONS -> R.string.stream_section_notifications
        StreamSection.RECENTS -> R.string.stream_section_recents
        StreamSection.AGENDA -> R.string.stream_section_agenda
        StreamSection.TASKS -> R.string.stream_section_tasks
        StreamSection.FEEDS -> R.string.stream_section_feeds
    }

@StringRes
private fun StreamSection.emptyRes(): Int =
    when (this) {
        StreamSection.NOW -> R.string.stream_empty_now
        StreamSection.NOTIFICATIONS -> R.string.stream_empty_notifications
        StreamSection.RECENTS -> R.string.stream_empty_recents
        StreamSection.AGENDA -> R.string.stream_empty_agenda
        StreamSection.TASKS -> R.string.stream_empty_tasks
        StreamSection.FEEDS -> R.string.stream_empty_feeds
    }

@StringRes
internal fun StreamView.titleRes(): Int =
    when (this) {
        StreamView.STACK -> R.string.stream_show_as_stack
        StreamView.LIST -> R.string.stream_show_as_list
    }

/** One section: its header (long-press or the options button for Show as and its layout) and what it holds. */
@Composable
internal fun StreamSectionBlock(
    section: StreamSection,
    view: StreamView,
    result: StreamSectionResult,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onShowAs: (StreamView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        StreamSectionHeader(section, view, canMoveUp, canMoveDown, onShowAs, callbacks)
        StreamSectionBody(section, view, result, env, callbacks)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamSectionHeader(
    section: StreamSection,
    view: StreamView,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onShowAs: (StreamView) -> Unit,
    callbacks: StreamCallbacks,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = stringResource(section.titleRes())
    Box {
        // The header sits on thin glass, so its text reads over any wallpaper.
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            weight = GlassWeight.Thin,
            shape = RiffleShapes.medium,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = StreamMinTarget)
                        .combinedClickable(
                            onLongClickLabel = stringResource(R.string.stream_header_long_press_label),
                            onLongClick = { menuOpen = true },
                            onClick = {},
                        )
                        .padding(start = RiffleSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(StreamMinTarget)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.stream_section_options, title),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ShowAsMenuItems(
                options = StreamView.entries,
                current = view,
                optionLabel = { option -> stringResource(option.titleRes()) },
                onSelect = { option ->
                    menuOpen = false
                    onShowAs(option)
                },
            )
            HorizontalDivider()
            if (canMoveUp) {
                MenuAction(R.string.stream_move_up) {
                    menuOpen = false
                    callbacks.onLayoutAction(StreamLayoutAction.Move(section, -1))
                }
            }
            if (canMoveDown) {
                MenuAction(R.string.stream_move_down) {
                    menuOpen = false
                    callbacks.onLayoutAction(StreamLayoutAction.Move(section, 1))
                }
            }
            MenuAction(R.string.stream_hide_section) {
                menuOpen = false
                callbacks.onLayoutAction(StreamLayoutAction.SetEnabled(section, false))
            }
        }
    }
}

@Composable
private fun MenuAction(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun StreamSectionBody(
    section: StreamSection,
    view: StreamView,
    result: StreamSectionResult,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    if (section == StreamSection.TASKS) {
        TasksSectionBody(result, callbacks) { StreamSectionResultBody(section, view, result, env, callbacks) }
    } else {
        StreamSectionResultBody(section, view, result, env, callbacks)
    }
}

@Composable
private fun StreamSectionResultBody(
    section: StreamSection,
    view: StreamView,
    result: StreamSectionResult,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    when (result) {
        StreamSectionResult.Loading ->
            StreamStateMessage(stringResource(R.string.stream_loading), showSpinner = !env.reducedMotion)
        StreamSectionResult.Empty -> StreamStateMessage(stringResource(section.emptyRes()))
        StreamSectionResult.Unavailable -> StreamStateMessage(stringResource(R.string.stream_unavailable))
        is StreamSectionResult.NotConnected -> StreamStateMessage(stringResource(R.string.stream_not_connected_tasks))
        StreamSectionResult.Off ->
            StreamStateMessage(
                message = stringResource(R.string.stream_off_message),
                actionLabel = stringResource(R.string.stream_off_action),
                onAction = { callbacks.onEnableSection(section) },
            )
        is StreamSectionResult.PermissionRequired ->
            StreamStateMessage(
                message = stringResource(R.string.stream_permission_message),
                actionLabel = stringResource(R.string.stream_permission_action),
                onAction = { callbacks.onRequestAccess(result.sources) },
            )
        is StreamSectionResult.Ready -> StreamSectionContent(section, view, result.groups, env, callbacks)
    }
}

/** An unlit, empty, permission, not-connected or unavailable section: thin glass with one honest sentence. */
@Composable
internal fun StreamStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        weight = GlassWeight.Thin,
        shape = RiffleShapes.medium,
        tint = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RiffleSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            if (showSpinner) CircularProgressIndicator(modifier = Modifier.padding(RiffleSpacing.xs))
            Text(
                text = message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = StreamMinTarget)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StreamSectionContent(
    section: StreamSection,
    view: StreamView,
    groups: List<StreamGroup>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        groups.forEach { group ->
            val groupKey = group.key.takeIf { section == StreamSection.NOTIFICATIONS }
            GroupFocusTarget(groupKey) {
                GroupHeading(section, group, env)
                if (view == StreamView.LIST) {
                    StreamRowsPanel(section, group, env, callbacks, groupKey)
                } else {
                    StreamGroupStack(section, group, env, callbacks, groupKey)
                }
            }
        }
    }
}

/**
 * Where the Dock asked the Stream to land: an app's group, scrolled into view once the surface has scrolled to its
 * section. Transient; never stored.
 */
@Stable
internal class StreamGroupFocus {
    var groupKey: String? by mutableStateOf(null)
        private set
    var nonce: Long by mutableLongStateOf(0L)
        private set

    fun request(key: String) {
        groupKey = key
        nonce++
    }

    fun handled() {
        groupKey = null
    }
}

internal val LocalStreamGroupFocus = staticCompositionLocalOf<StreamGroupFocus?> { null }

/** A group (its heading and its cards or rows) that scrolls itself into view when the Dock asked for it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupFocusTarget(
    groupKey: String?,
    content: @Composable () -> Unit,
) {
    val focus = LocalStreamGroupFocus.current
    if (groupKey == null || focus == null) {
        Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) { content() }
        return
    }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focus.nonce, focus.groupKey) {
        if (focus.groupKey == groupKey) {
            requester.bringIntoView()
            focus.handled()
        }
    }
    Column(
        modifier = Modifier.bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) { content() }
}

@Composable
private fun StreamGroupStack(
    section: StreamSection,
    group: StreamGroup,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
) {
    when (section) {
        StreamSection.RECENTS -> RecentAppsRow(group.items, env, callbacks, groupKey)
        StreamSection.TASKS ->
            TasksCard(group.items.filterIsInstance<TaskContent>(), env, callbacks, Modifier.fillMaxWidth())
        else -> StreamStack(group.items, env, callbacks, groupKey = groupKey ?: "${section.storedId}/${group.key}")
    }
}

@Composable
private fun StreamRowsPanel(
    section: StreamSection,
    group: StreamGroup,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        weight = GlassWeight.Thin,
        shape = RiffleShapes.medium,
        tint = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            group.items.forEachIndexed { index, content ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = RiffleSpacing.l))
                val next =
                    section == StreamSection.NOW &&
                        content is EventContent &&
                        content.kind != EventKind.ALARM
                StreamRow(content, env, callbacks, groupKey, showCountdown = next)
            }
        }
    }
}

/** The small heading over a group: an app, a feed, a day, "Often used", and how many more were left out. */
@Composable
private fun GroupHeading(
    section: StreamSection,
    group: StreamGroup,
    env: StreamEnvironment,
) {
    val label = groupLabel(section, group, env) ?: return
    val more = group.hiddenCount.takeIf { it > 0 }?.let { stringResource(R.string.stream_more_count, it) }
    Row(modifier = Modifier.fillMaxWidth()) {
        GlassSurface(
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() },
                )
                more?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun groupLabel(
    section: StreamSection,
    group: StreamGroup,
    env: StreamEnvironment,
): String? =
    when (section) {
        StreamSection.AGENDA -> runCatching { LocalDate.parse(group.key) }.getOrNull()?.let { dayLabel(it, env) }
        StreamSection.RECENTS ->
            if (group.key == StreamGroupKeys.FREQUENT) stringResource(R.string.stream_recents_frequent) else null
        StreamSection.NOTIFICATIONS, StreamSection.FEEDS -> group.label?.takeIf { it.isNotBlank() }
        StreamSection.NOW, StreamSection.TASKS -> null
    }

@Composable
private fun dayLabel(
    day: LocalDate,
    env: StreamEnvironment,
): String {
    val today = env.formats.today()
    return when (day) {
        today -> stringResource(R.string.stream_day_today)
        today.plusDays(1) -> stringResource(R.string.stream_day_tomorrow)
        else -> env.formats.dayLabel(day)
    }
}

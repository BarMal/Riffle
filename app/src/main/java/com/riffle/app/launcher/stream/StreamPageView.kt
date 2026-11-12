@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
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

/** The glyph of a section, for the header, the spine and the Stage rail of a page that has no app icon. */
internal fun StreamSection.glyph(): ImageVector =
    when (this) {
        StreamSection.NOW -> Icons.Filled.PlayArrow
        StreamSection.NOTIFICATIONS -> Icons.Filled.Notifications
        StreamSection.RECENTS -> StreamIcons.Recents
        StreamSection.AGENDA -> StreamIcons.Agenda
        StreamSection.TASKS -> Icons.Filled.CheckCircle
        StreamSection.FEEDS -> StreamIcons.Feeds
    }

/** The page's name: the app or feed, or its section. */
@Composable
internal fun StreamPage.title(): String = label ?: stringResource(section.titleRes())

/** The page's items as one spoken phrase ("3 items"); empty for a page that holds only a state. */
@Composable
internal fun StreamPage.countText(): String =
    if (count <= 0) "" else pluralStringResource(R.plurals.stream_page_items, count, count)

/**
 * One page of the Stream (ADR 0003, Views): a header (the app's icon or the section's glyph, the name, how many items,
 * and the options button or long-press for Show as, Move and Hide) over the page's content as its section's stored
 * view, a vertical Stack of overlapping cards or a List of rows. A labelled pane for assistive technology. Text-heavy
 * content stops at 640 dp.
 */
@Composable
internal fun StreamPageView(
    page: StreamPage,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onShowAs: (StreamView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = page.title()
    Box(modifier = modifier.fillMaxSize().semantics { paneTitle = title }, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = StreamMaxReadableWidth)
                    .fillMaxSize()
                    .padding(horizontal = RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            StreamPageHeader(page, title, env, canMoveUp, canMoveDown, onShowAs, callbacks)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { StreamPageBodyView(page, env, callbacks) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamPageHeader(
    page: StreamPage,
    title: String,
    env: StreamEnvironment,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onShowAs: (StreamView) -> Unit,
    callbacks: StreamCallbacks,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val sectionTitle = stringResource(page.section.titleRes())
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
                        .padding(start = RiffleSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                StreamPageGlyph(page, env, HeaderIconSize)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    val count = page.countText()
                    if (count.isNotEmpty()) {
                        Text(
                            text = count,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(StreamMinTarget)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.stream_section_options, sectionTitle),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PageMenuItems(
                page = page,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onClose = { menuOpen = false },
                onShowAs = onShowAs,
                callbacks = callbacks,
            )
        }
    }
}

/** Show as (for the page's whole section), then the section's Move and Hide: the one menu every page shares. */
@Composable
private fun ColumnScope.PageMenuItems(
    page: StreamPage,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClose: () -> Unit,
    onShowAs: (StreamView) -> Unit,
    callbacks: StreamCallbacks,
) {
    ShowAsMenuItems(
        options = StreamView.entries,
        current = page.view,
        optionLabel = { option -> stringResource(option.titleRes()) },
        onSelect = { option ->
            onClose()
            onShowAs(option)
        },
    )
    HorizontalDivider()
    if (canMoveUp) {
        MenuAction(R.string.stream_move_up) {
            onClose()
            callbacks.onLayoutAction(StreamLayoutAction.Move(page.section, -1))
        }
    }
    if (canMoveDown) {
        MenuAction(R.string.stream_move_down) {
            onClose()
            callbacks.onLayoutAction(StreamLayoutAction.Move(page.section, 1))
        }
    }
    MenuAction(R.string.stream_hide_section) {
        onClose()
        callbacks.onLayoutAction(StreamLayoutAction.SetEnabled(page.section, false))
    }
}

@Composable
private fun MenuAction(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

private val HeaderIconSize = RiffleSpacing.xxl

/** The app's icon for an app page, else the section's glyph. Decorative: the title names the page. */
@Composable
internal fun StreamPageGlyph(
    page: StreamPage,
    env: StreamEnvironment,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val handle = page.lead?.takeIf { page.isGroupPage && page.section == StreamSection.NOTIFICATIONS }?.iconHandle()
    if (handle != null) {
        StreamIcon(handle, env, size, modifier)
    } else {
        Icon(
            imageVector = page.section.glyph(),
            contentDescription = null,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A page's scroll area: a column that scrolls vertically (states, Lists, Recents and Tasks). */
@Composable
private fun PageScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = RiffleSpacing.l),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        content = content,
    )
}

@Composable
private fun StreamPageBodyView(
    page: StreamPage,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    when (val body = page.body) {
        is StreamPageBody.State ->
            PageScroll {
                if (page.section == StreamSection.TASKS) {
                    TasksSectionBody(
                        body.result,
                        callbacks,
                    ) { StreamStateBody(page.section, body.result, env, callbacks) }
                } else {
                    StreamStateBody(page.section, body.result, env, callbacks)
                }
            }
        is StreamPageBody.Content -> StreamContentBody(page, body, env, callbacks)
    }
}

/** What a section that cannot show content says, with its way out. Content results are not drawn here. */
@Composable
private fun StreamStateBody(
    section: StreamSection,
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
        is StreamSectionResult.Ready -> Unit
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

/**
 * The page's content as its section's view. Stack: a vertical pile of cards (apps are tiles, so Recents is a grid of
 * them, and Tasks is one card under the quick-add row). List: rows on one thin glass panel per group.
 */
@Composable
private fun StreamContentBody(
    page: StreamPage,
    body: StreamPageBody.Content,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
) {
    val section = page.section
    // Only a notification can be hidden by its app group; the rest carry no group for the exclusions flow.
    val hideGroupKey = page.groupKey.takeIf { section == StreamSection.NOTIFICATIONS }
    when {
        section == StreamSection.TASKS ->
            PageScroll {
                TasksSectionBody(StreamSectionResult.Ready(body.groups), callbacks) {
                    val tasks = body.items
                    if (page.view == StreamView.LIST) {
                        StreamRowsPanel(section, tasks, env, callbacks, hideGroupKey)
                    } else {
                        TasksCard(tasks.filterIsInstance<TaskContent>(), env, callbacks, Modifier.fillMaxWidth())
                    }
                }
            }
        page.view == StreamView.LIST ->
            PageScroll {
                body.groups.forEach { group ->
                    GroupHeading(section, group, env)
                    StreamRowsPanel(section, group.items, env, callbacks, hideGroupKey)
                }
            }
        section == StreamSection.RECENTS ->
            PageScroll {
                body.groups.forEach { group ->
                    GroupHeading(section, group, env)
                    RecentAppsGrid(group.items, env, callbacks, hideGroupKey)
                }
            }
        else ->
            StreamStack(
                items = body.items,
                env = env,
                callbacks = callbacks,
                groupKey = hideGroupKey,
                stackKey = page.key,
                captions = if (section == StreamSection.AGENDA) agendaCaptions(body.groups, env) else emptyMap(),
            )
    }
}

@Composable
private fun StreamRowsPanel(
    section: StreamSection,
    items: List<com.riffle.core.domain.launcher.stream.StreamContent>,
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
            items.forEachIndexed { index, content ->
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

/** Recents as tiles: a wrapping grid that scrolls with the page, since an app has no card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentAppsGrid(
    items: List<com.riffle.core.domain.launcher.stream.StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        items.filterIsInstance<com.riffle.core.domain.launcher.stream.RecentAppContent>().forEach { app ->
            RecentAppTile(app, env, callbacks, groupKey)
        }
    }
}

/** The small heading over a group of a page that holds several: a day of the agenda, "Often used". */
@Composable
private fun GroupHeading(
    section: StreamSection,
    group: StreamGroup,
    env: StreamEnvironment,
) {
    val label = groupLabel(section, group, env) ?: return
    Row(modifier = Modifier.fillMaxWidth()) {
        GlassSurface(
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs)
                        .semantics { heading() },
            )
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
        StreamSection.NOTIFICATIONS, StreamSection.FEEDS, StreamSection.NOW, StreamSection.TASKS -> null
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

/** The day each agenda event is on, so a pile of cards that spans days still says which day a card is for. */
@Composable
private fun agendaCaptions(
    groups: List<StreamGroup>,
    env: StreamEnvironment,
): Map<String, String> =
    buildMap {
        groups.forEach { group ->
            val label = groupLabel(StreamSection.AGENDA, group, env) ?: return@forEach
            group.items.forEach { put(it.id.value, label) }
        }
    }

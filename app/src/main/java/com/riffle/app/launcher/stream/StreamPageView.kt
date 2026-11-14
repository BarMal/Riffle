@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dockFan
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import java.time.LocalDate

@StringRes
internal fun StreamSection.titleRes(): Int =
    when (this) {
        StreamSection.NOW -> R.string.stream_section_now
        StreamSection.NOTIFICATIONS -> R.string.stream_section_notifications
        StreamSection.RECENTS -> R.string.stream_section_recents
        StreamSection.TODAY -> R.string.stream_section_today
        StreamSection.FEEDS -> R.string.stream_section_feeds
        StreamSection.SHORTCUTS -> R.string.stream_section_shortcuts
    }

@StringRes
private fun StreamSection.emptyRes(): Int =
    when (this) {
        StreamSection.NOW -> R.string.stream_empty_now
        StreamSection.NOTIFICATIONS -> R.string.stream_empty_notifications
        StreamSection.RECENTS -> R.string.stream_empty_recents
        StreamSection.TODAY -> R.string.stream_empty_today
        StreamSection.FEEDS -> R.string.stream_empty_feeds
        StreamSection.SHORTCUTS -> R.string.stream_empty_shortcuts
    }

@StringRes
internal fun StreamView.titleRes(): Int =
    when (this) {
        StreamView.STACK -> R.string.stream_show_as_stack
        StreamView.LIST -> R.string.stream_show_as_list
    }

/** The glyph of a section, for the Dock's tile and the Stage rail of a page that has no app icon. */
internal fun StreamSection.glyph(): ImageVector =
    when (this) {
        StreamSection.NOW -> Icons.Filled.PlayArrow
        StreamSection.NOTIFICATIONS -> Icons.Filled.Notifications
        StreamSection.RECENTS -> StreamIcons.Recents
        StreamSection.TODAY -> StreamIcons.Agenda
        StreamSection.FEEDS -> StreamIcons.Feeds
        StreamSection.SHORTCUTS -> Icons.Filled.CheckCircle
    }

/** The page's name: the app or feed, or its section. */
@Composable
internal fun StreamPage.title(): String = label ?: stringResource(section.titleRes())

/** The page's items as one spoken phrase ("3 items"); empty for a page that holds only a state. */
@Composable
internal fun StreamPage.countText(): String =
    if (count <= 0) "" else pluralStringResource(R.plurals.stream_page_items, count, count)

/**
 * One page of the Stream (ADR 0003, Views, Amendment 3): the page's content as its section's stored view, a vertical
 * Stack of overlapping cards or a List of rows, with no header of its own: the Dock is the page indicator, and the
 * page's options (Show as, Move, Hide) are on a long-press of its Dock tile. A labelled pane for assistive technology.
 * Text-heavy content stops at 640 dp.
 */
@Composable
internal fun StreamPageView(
    page: StreamPage,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
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
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { StreamPageBodyView(page, env, callbacks) }
        }
    }
}

/** The app's icon for an app page, else the section's glyph. Decorative: the title names the page. */
@Composable
internal fun StreamPageGlyph(
    page: StreamPage,
    env: StreamEnvironment,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val handle =
        (page.lead as? AppShortcutContent)?.let { ItemImageKeys.appIcon(it.appIdentity) }
            ?: page.lead?.takeIf { page.isGroupPage && page.section == StreamSection.NOTIFICATIONS }?.iconHandle()
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

/** A page's scroll area: a column that scrolls vertically (states and Lists). */
@Composable
internal fun PageScroll(content: @Composable ColumnScope.() -> Unit) {
    // Rises out of the Dock as one piece, with the front card's timing, when the Dock's trampoline opens the Stream.
    Column(
        modifier =
            Modifier
                .dockFan(LocalDockFan.current)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = RiffleSpacing.l),
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
                val result = body.result
                if (page.section == StreamSection.TODAY && result is StreamSectionResult.NotConnected) {
                    // No task account and nothing else to show on Today: the connect state, as a card.
                    TasksSectionBody(result, callbacks) {}
                } else {
                    StreamStateBody(page.section, result, env, callbacks)
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
        StreamSectionResult.Unavailable ->
            StreamStateMessage(
                message = stringResource(R.string.stream_unavailable),
                actionLabel = stringResource(R.string.stream_task_retry),
                onAction = { callbacks.onRetrySection(section) },
            )
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
 * The page's content as its section's view. Stack: a vertical pile of cards, the same for every section (Today's
 * cards are its next event, its checklist, later events and tomorrow; a pinned app's are its shortcuts; Recents' are
 * its apps).
 * List: rows on one thin glass panel per group (Today: the agenda, then the tasks).
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
        section == StreamSection.TODAY && page.view == StreamView.LIST -> TodayListBody(body, env, callbacks)
        section == StreamSection.TODAY ->
            StreamStack(
                entries = todayEntries(body, env),
                env = env,
                callbacks = callbacks,
                groupKey = null,
                stackKey = page.key,
            )
        page.view == StreamView.LIST ->
            PageScroll {
                body.groups.forEach { group ->
                    GroupHeading(section, group, env)
                    StreamRowsPanel(section, group.items, env, callbacks, hideGroupKey)
                }
                body.gaps.forEach { gap -> StreamShortcutsGap(gap) }
            }
        else ->
            StreamStack(
                entries = pageEntries(page, body),
                env = env,
                callbacks = callbacks,
                groupKey = hideGroupKey,
                stackKey = page.key,
            )
    }
}

/** The cards of a page's stack: its content in order (each with its caption), then the notices of its gaps. */
@Composable
private fun pageEntries(
    page: StreamPage,
    body: StreamPageBody.Content,
): List<StackEntry> {
    val captions = if (page.section == StreamSection.RECENTS) recentCaptions(body.groups) else emptyMap()
    val content = body.items.map { item -> ContentEntry(item, caption = captions[item.id.value]) }
    return content + body.gaps.shortcutNotices()
}

/** The explanation of a shortcuts page's gap in a List: the same sentence the stack's notice card says. */
@Composable
private fun StreamShortcutsGap(gap: com.riffle.core.domain.launcher.stream.StreamGap) {
    if (gap.source == com.riffle.core.domain.launcher.stream.StreamSourceIds.SHORTCUTS) {
        StreamStateMessage(noticeText(Notice.ShortcutsNeedHome))
    }
}

@Composable
internal fun StreamRowsPanel(
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

/** The small heading over a group of a page that holds several: a day of the agenda, "Often used". */
@Composable
internal fun GroupHeading(
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
        StreamSection.TODAY -> runCatching { LocalDate.parse(group.key) }.getOrNull()?.let { dayLabel(it, env) }
        StreamSection.RECENTS ->
            if (group.key == StreamGroupKeys.FREQUENT) stringResource(R.string.stream_recents_frequent) else null
        StreamSection.NOTIFICATIONS, StreamSection.FEEDS, StreamSection.NOW, StreamSection.SHORTCUTS -> null
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

/** "Often used" on the apps of Recents' second group, so a pile that holds both still says which are which. */
@Composable
private fun recentCaptions(groups: List<StreamGroup>): Map<String, String> {
    val often = stringResource(R.string.stream_recents_frequent)
    return buildMap {
        groups.filter { it.key == StreamGroupKeys.FREQUENT }.forEach { group ->
            group.items.forEach { put(it.id.value, often) }
        }
    }
}

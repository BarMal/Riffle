package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.ics.IcsFeedsText
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.app.launcher.workspace.sourceAccessRouteFor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Settings > Sources. Reachable only from the Developer section while Workspaces (preview) is on. While it is
 * open each source has one status subscription (shared with the containers); closing it releases them all.
 */
@Composable
internal fun SettingsSourcesPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit = {},
) {
    val host = LocalWorkspaceSettingsHost.current
    if (host == null) {
        SettingsPreviewOffNote()
    } else {
        val controller = host.sources
        DisposableEffect(controller) {
            controller.open()
            onDispose { controller.close() }
        }
        val statuses by controller.statuses.collectAsState()
        val disabled by controller.disabled.collectAsState()
        val rows = remember(controller, statuses, disabled) { controller.rows(statuses, disabled) }
        val icsFeeds = host.icsFeeds
        if (icsFeeds != null) {
            DisposableEffect(icsFeeds) {
                icsFeeds.open()
                onDispose { icsFeeds.close() }
            }
        }
        val icsState by (icsFeeds?.state ?: NoIcsFeeds).collectAsState()
        SourcesSettingsContent(
            rows = rows,
            calendarAccess = state.calendarAccessStatus,
            onToggle = controller::setEnabled,
            onAllow = host.onRequestSourceAccess,
            onOpenHiddenItems = { onPageSelected(SettingsPage.EXCLUSIONS) },
            calendarFeeds = icsState.takeIf { icsFeeds != null },
            onRefreshCalendarFeeds = { icsFeeds?.refreshAll() },
            onOpenCalendarFeeds = { onPageSelected(SettingsPage.ICS_FEEDS) },
        )
    }
}

/** The page itself, with no controller. At medium and expanded widths the rows run in two columns. */
@Suppress("LongParameterList") // Fixed-state page content: rows, callbacks and the optional feed section.
@Composable
internal fun SourcesSettingsContent(
    rows: List<SourceRow>,
    calendarAccess: CalendarAccessStatus,
    onToggle: (SourceId, Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
    onOpenHiddenItems: () -> Unit = {},
    calendarFeeds: IcsFeedsUiState? = null,
    onRefreshCalendarFeeds: () -> Unit = {},
    onOpenCalendarFeeds: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            text = SourcesSettingsText.SOURCES_INTRO,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= TWO_PANE_MIN_WIDTH_DP.dp) 2 else 1
            Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                rows.chunkedBy(columns).forEachIndexed { index, part ->
                    Column(modifier = Modifier.weight(1f)) {
                        SettingsSection(title = if (index == 0) SourcesSettingsText.TITLE else "More sources") {
                            part.forEach { row ->
                                SourceListItem(row, calendarAccess, onToggle, onAllow)
                            }
                        }
                    }
                }
            }
        }
        if (calendarFeeds != null) {
            SettingsSection(title = IcsFeedsText.TITLE) {
                IcsRefreshRow(state = calendarFeeds, onRefresh = onRefreshCalendarFeeds)
                SettingsClickableRow(
                    modifier = Modifier.testTag(ICS_FEEDS_ROW_TEST_TAG),
                    title = IcsFeedsText.TITLE,
                    subtitle = IcsFeedsText.countLabel(calendarFeeds.rows.size),
                    onClick = onOpenCalendarFeeds,
                )
            }
        }
        SettingsSection(title = "Hidden items") {
            SettingsClickableRow(
                modifier = Modifier.testTag(HIDDEN_ITEMS_ROW_TEST_TAG),
                title = ExclusionsSettingsText.TITLE,
                subtitle = ExclusionsSettingsText.ROW_SUBTITLE,
                onClick = onOpenHiddenItems,
            )
        }
    }
}

internal const val HIDDEN_ITEMS_ROW_TEST_TAG = "sources-hidden-items-row"
internal const val ICS_FEEDS_ROW_TEST_TAG = "sources-ics-feeds-row"

private val NoIcsFeeds = MutableStateFlow(IcsFeedsUiState())

/** Splits [this] into [columns] consecutive parts of near-equal size (one part when [columns] is 1). */
internal fun <T> List<T>.chunkedBy(columns: Int): List<List<T>> {
    if (columns <= 1 || size <= 1) return listOf(this)
    val first = (size + 1) / 2
    return listOf(take(first), drop(first))
}

/**
 * One source: name, what it shows, its status as text, an Allow button with its rationale when it needs
 * permission (only ever a tap away, never automatic) and the switch. The whole row is one toggle for TalkBack.
 */
@Composable
private fun SourceListItem(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onToggle: (SourceId, Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RiffleShapes.medium)
                .toggleable(
                    value = row.enabled,
                    role = Role.Switch,
                    onValueChange = { enabled -> onToggle(row.id, enabled) },
                )
                .semantics {
                    contentDescription =
                        SourcesSettingsText.statusDescription(row.title, row.status) + ". " + row.description
                },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = row.title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { SourceDetails(row = row, calendarAccess = calendarAccess, onAllow = onAllow) },
        trailingContent = { Switch(checked = row.enabled, onCheckedChange = null) },
    )
}

@Composable
private fun SourceDetails(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onAllow: (SourceId) -> Unit,
) {
    val route = sourceAccessRouteFor(row.id)
    val needsPermission = row.enabled && row.status == SourceStatus.NEEDS_PERMISSION
    val allowLabel = SourcesSettingsText.allowLabel(route, calendarAccess).takeIf { needsPermission }
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        Text(
            text = row.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceStatusChip(row.status)
        if (allowLabel != null) {
            SourcesSettingsText.rationale(route, calendarAccess)?.let { rationale ->
                Text(
                    text = rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { onAllow(row.id) },
                modifier = Modifier.heightIn(min = RiffleSpacing.xxxl),
            ) {
                Text(text = allowLabel)
            }
        }
    }
}

/** The status as words in a small chip: never colour alone. */
@Composable
private fun SourceStatusChip(status: SourceStatus) {
    val color =
        when (status) {
            SourceStatus.READY -> MaterialTheme.colorScheme.secondaryContainer
            SourceStatus.NEEDS_PERMISSION -> MaterialTheme.colorScheme.tertiaryContainer
            SourceStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
            SourceStatus.LOADING, SourceStatus.OFF -> MaterialTheme.colorScheme.surfaceVariant
        }
    Surface(
        shape = RiffleShapes.small,
        color = color,
        tonalElevation = RiffleElevation.level0,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xxs),
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
